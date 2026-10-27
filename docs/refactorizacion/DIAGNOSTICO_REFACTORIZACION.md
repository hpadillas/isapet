# Diagnóstico técnico y refactorización — Compra de bonos (F-05)

Ingeniería de Software II · Taller de refactorización 1

> Convenciones de este informe
> - Las rutas son relativas a la raíz del repositorio y los números de línea corresponden al commit
>   `3313321` (rama `main`), que es el código auditado.
> - **Hecho**: verificado leyendo el código o ejecutando pruebas. **Hipótesis**: plausible, pero no
>   comprobado con una prueba; se indica explícitamente.
> - La IA se usó como apoyo para rastrear el código y redactar; cada ubicación, fragmento y
>   consecuencia se verificó manualmente contra el repositorio y contra la ejecución de las pruebas.
>   No se enviaron secretos ni datos personales.

---

## 1. Contexto

| Campo | Valor |
|---|---|
| Equipo | Hernando Padilla Serna, Ferney Cardona García, Javier Rivera Aristizábal |
| Repositorio | <https://github.com/hpadillas/isapet> |
| Tecnología | Backend Java 17 + Spring Boot 3.4.5 (Web, Data JPA, Security, Validation), PostgreSQL 16 con Flyway; frontend Angular (componentes *standalone*); Docker Compose |
| Arquitectura | Monolito en capas: `controller` → `service` → `repository` (Spring Data JPA) → PostgreSQL, con `entity` (JPA + comportamiento de dominio), `dto`, `mapper` y `exception` |
| Funcionalidad seleccionada | **F-05 · Compra de bonos de consulta o procedimiento** (y su consumo al registrar la prestación) |
| Requisitos relacionados (SyRS ISAPET) | **RF005** (gestionar la compra de bonos; el valor depende del plan y se calcula automáticamente), **CU5** (Comprar bono de consulta o procedimiento), **REGLA 4** (solo afiliados con plan activo pueden solicitar bonos), **REGLA 5** (el copago depende del plan contratado), RF008/RF009 (cobro y registro de cada pago), RF013 (historial de prestaciones) |

**Por qué esta funcionalidad.** Es una transacción de negocio completa que atraviesa varias entidades:
`Afiliado`, `Afiliacion`, `Mascota`, `Plan`/`VersionPlan`, `ServicioVeterinario`, `BeneficioPlan`,
`Bono`, `Pago`, `CuotaPlan` y `Prestacion`. Decide dinero (cuánto paga el afiliado y si paga o no un
bono) y tiene dos canales (pago en ISAPET y pago en línea con webhook).

**Reglas de negocio implementadas** (leídas del código, `BonoService.evaluar`):
el afiliado debe existir y tener afiliación; la mascota debe ser suya y estar cubierta por el plan; el
servicio debe estar activo; el plan debe tener versión comercial vigente, no estar pendiente de
activación ni suspendido por mora; y **solo se venden bonos cuando se agotaron las atenciones incluidas
del periodo** (si el plan es ilimitado no se venden). El valor del bono es `valorBase × (100 − descuento
del plan) / 100`, redondeado a pesos (`TarifaBono.calcular`).

---

## 2. Arquitectura real

### 2.1 Componentes que participan

| Capa | Componente | Responsabilidad en F-05 |
|---|---|---|
| Frontend | `frontend/src/app/features/bonos/pages/bonos-afiliado/bonos-afiliado.component.ts` | Formulario de compra, cotización en vivo, elección de pago en ISAPET o en línea |
| Frontend | `frontend/src/app/features/bonos/services/bono-api.service.ts` | `GET /bonos`, `GET /bonos/cotizacion`, `POST /bonos` |
| Web | `backend/.../controller/AfiliacionController.java` | `GET /api/v1/afiliados/{id}/bonos`, `GET .../bonos/cotizacion`, `POST .../bonos` (L170, L181, L196) |
| Web | `backend/.../controller/PagoController.java` | `POST /api/v1/afiliados/{id}/bonos/en-linea` (L58) |
| Web | `backend/.../controller/PasarelaController.java` | `POST /api/v1/pasarela/webhook` (confirmación del pago en línea) |
| Servicio | `backend/.../service/BonoService.java` | Elegibilidad (`evaluar`), cotización, compra manual y en línea |
| Servicio | `backend/.../service/PagoService.java` | Libro de pagos: `registrarManualBono`, `iniciarPagoBono`, `aplicarResultado` |
| Servicio | `backend/.../service/PrestacionService.java` | Consumo del bono o del cupo incluido al registrar la atención |
| Servicio | `backend/.../service/BeneficiosPlanResolver.java` | Versión comercial vigente y % de descuento del plan |
| Servicio | `backend/.../service/PeriodoAfiliacion.java` | Periodo mensual que contiene una fecha |
| Dominio | `entity/Bono.java` | Máquina de estados `PENDIENTE_PAGO → DISPONIBLE → USADO` / `ANULADO` |
| Dominio | `entity/TarifaBono.java` | *Value object* del cálculo del valor del bono |
| Dominio | `entity/Afiliacion.java` | `cubre(mascota)`, `isPendienteActivacion()`, cancelación programada |
| Persistencia | `AfiliacionRepository` (`findByAfiliadoIdForUpdate`, bloqueo pesimista), `BonoRepository`, `PagoRepository`, `PrestacionRepository.contarIncluidasEnPlan`, `CuotaPlanRepository.planSuspendido`, `MascotaRepository`, `ServicioVeterinarioRepository` | Acceso a datos |
| Pruebas | `backend/src/test/.../integration/AfiliacionBonosPrestacionesIntegrationTest.java` (F-03 a F-06), `CancelacionYSuspensionIntegrationTest`, `LibroPagosYPasarelaIntegrationTest`, `entity/TarifaBonoTest` | Protegen el comportamiento contra PostgreSQL real |

### 2.2 Flujo de la compra (pago en ISAPET)

```
BonosAfiliadoComponent ──POST /api/v1/afiliados/{id}/bonos──▶ AfiliacionController.comprarBono
  └─▶ BonoService.comprar  (@Transactional)
        ├─ AfiliacionRepository.findByAfiliadoIdForUpdate   ← bloqueo: serializa con el registro de prestaciones
        ├─ evaluar(...)
        │    ├─ AfiliadoRepository.existsById
        │    ├─ MascotaRepository.findById + Afiliacion.cubre
        │    ├─ ServicioVeterinarioRepository.findById (activo)
        │    ├─ BeneficiosPlanResolver.versionVigente ──▶ VersionPlanRepository
        │    ├─ CuotaPlanRepository.planSuspendido
        │    ├─ PeriodoAfiliacion.queContiene + PrestacionRepository.contarIncluidasEnPlan   ← cupo del periodo
        │    └─ TarifaBono.calcular(valorBase, BeneficiosPlanResolver.porcentajeDescuento)
        ├─ BonoRepository.saveAndFlush(new Bono(...))
        └─ PagoService.registrarManualBono ──▶ PagoRepository.save (APROBADO)
```

**Pago en línea:** `PagoController.comprarBonoEnLinea` → `BonoService.comprarEnLinea` (mismo `evaluar`,
bono en `PENDIENTE_PAGO`) → `PagoService.iniciarPagoBono` → URL de la pasarela. La pasarela llama a
`POST /api/v1/pasarela/webhook` → `WebhookPasarelaService` valida la firma HMAC (`FirmaPasarela`) →
`PagoService.aplicarResultado` → `Bono.confirmarPago()` o `Bono.anularPorPagoRechazado()`.

**Consumo:** `POST /prestaciones` → `PrestacionService.registrar`: con `bonoId` valida y marca el bono
como `USADO`; sin bono, `cupoIncluidoDisponible` decide si la atención es `PLAN` (consume cupo) o
`PARTICULAR`. **Esta decisión y la de "¿se puede vender un bono?" dependen de la misma regla de cupo.**

### 2.3 Arquitectura documentada vs. implementada

- El SyRS de ISAPET (Ingeniería de Software I) describe el contexto (aplicación web + Web API REST +
  base de datos, IC-001/IS-003/IS-004) pero **no documenta capas ni dependencias internas**, así que no
  es posible contrastar decisiones de diseño interno contra un documento. *Información faltante.*
- La implementación respeta el sentido de dependencias por capas: los controladores solo delegan en
  servicios, no acceden a repositorios ni calculan reglas (verificado en `AfiliacionController` y
  `PagoController`). El dominio no es anémico: `Bono`, `Afiliacion` y `TarifaBono` encapsulan
  invariantes.
- En la raíz del repositorio hay dos scripts (`fix_backend.py`, `refactor_backend.py`, commit `a2c82a1`)
  que describen una migración a arquitectura hexagonal (`domain/port/in`, `infrastructure/...`) con
  rutas absolutas del equipo de un integrante; **esa estructura no existe en el código**. Se registra
  como deuda técnica pendiente (sección 7), no como hallazgo de esta funcionalidad.

---

## 3. Hallazgos

Resumen (detalle en las fichas):

| ID | Tipo | Ubicación principal | Hallazgo | Estado |
|---|---|---|---|---|
| H-01 | Deuda técnica (duplicación de regla de dominio, SRP/cohesión) | `BonoService.evaluar` L164-188 · `PrestacionService.cupoIncluidoDisponible` L142-150 | La regla del cupo de atenciones incluidas está reimplementada en 4 servicios | Seleccionado |
| H-02 | Riesgo arquitectónico (cohesión de la capa web, SRP) | `AfiliacionController` · `PagoController` L58 | Los endpoints de bonos están repartidos entre dos controladores; uno concentra 5 funcionalidades | Pendiente |
| H-03 | Riesgo arquitectónico (seguridad / configuración) | `FirmaPasarela` L24 · `application.yml` L48 · `docker-compose.yml` L82 | Secreto del webhook con valor por defecto público; el webhook es `permitAll` | Pendiente |
| H-04 | Oportunidad de mejora (regla repartida / validación después de persistir) | `BonoService.comprarEnLinea` L128-136 · `PagoService.iniciarPagoBono` L112-114 | Un bono de valor 0 se inserta y luego se rechaza por rollback | Pendiente |
| H-05 | Oportunidad de mejora (duplicación menor) | `BonoService` L150-154 · `PrestacionService` L183-190 · `AfiliacionService` L203-213 | Validación "la mascota pertenece al afiliado" copiada en 3 servicios | Pendiente |

No se encontraron **defectos funcionales** en la funcionalidad: la suite existente del backend
(122 pruebas) pasa sobre el código original (`evidencias/01-pruebas-antes.txt`).

---

### H-01 · Regla del cupo de atenciones incluidas duplicada en cuatro servicios

**ID:** H-01 | **Tipo:** deuda técnica (duplicación de una regla de dominio; afecta SRP y cohesión)

**Ubicación:**
- `backend/src/main/java/com/isapet/service/BonoService.java`, `BonoService.evaluar`, L164-188 (venta de bonos)
- `backend/src/main/java/com/isapet/service/PrestacionService.java`, `PrestacionService.cupoIncluidoDisponible`, L142-150 (cobertura PLAN/PARTICULAR)
- `backend/src/main/java/com/isapet/service/AfiliacionService.java`, `response`, L226-237 y L272 (atenciones restantes que se muestran)
- `backend/src/main/java/com/isapet/service/NotificacionService.java`, `recordarFinDePeriodo`, L263-280 (saldo en el aviso de fin de periodo)

**Evidencia:**

```java
// BonoService.evaluar (L164-188)
Long planId = afiliacion.getPlan().getId();
VersionPlan version = beneficiosResolver.versionVigente(planId, LocalDate.now(clock))
        .orElseThrow(...);
...
Integer limite = version.getLimiteAtenciones();
if (limite == null) { throw ... "incluye atenciones ilimitadas" ... }
PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), LocalDate.now(clock));
long usadas = prestacionRepository.contarIncluidasEnPlan(afiliadoId, periodo.desde(), periodo.hastaExclusiva());
if (usadas < limite) { throw ... "le quedan %d de %d atenciones" ... }
```

```java
// PrestacionService.cupoIncluidoDisponible (L142-150)
PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), fecha);
return beneficiosResolver.versionVigente(afiliacion.getPlan().getId(), fecha).flatMap(version -> {
    Integer limite = version.getLimiteAtenciones();
    if (limite == null) { return Optional.of(new CupoIncluido(periodo, false)); }
    long usadas = prestacionRepository.contarIncluidasEnPlan(afiliadoId, periodo.desde(), periodo.hastaExclusiva());
    return usadas < limite ? Optional.of(new CupoIncluido(periodo, usadas + 1 == limite)) : Optional.empty();
});
```

```java
// AfiliacionService.response (L226, L237, L272): cuenta con la lista completa, no con el count
PeriodoAfiliacion periodo = PeriodoAfiliacion.queContiene(afiliacion.getFechaInicio(), hoy);
List<PrestacionResponse> incluidas = prestacionRepository.buscarIncluidasEnPlan(...)...;
Integer limite = version.getLimiteAtenciones();
... limite == null ? null : Math.max(0, limite - incluidas.size()),
```

**Observación.** "Cuántas atenciones incluidas le quedan al afiliado en el periodo que contiene una
fecha" es **una sola regla del negocio** (RF005 + REGLA 4/5: el bono paga lo que el plan ya no cubre),
pero cada servicio la recompone a mano con los mismos cuatro pasos: periodo vigente → versión
comercial vigente → límite (`null` = ilimitado) → conteo de prestaciones con cobertura `PLAN`. Además,
`BonoService` depende de `PrestacionRepository` solo para contar prestaciones: conoce un detalle de
persistencia del módulo de prestaciones que no es su responsabilidad.

**Criterio.** DRY y *única fuente de verdad* de una regla de dominio; SRP aplicado como "una razón de
cambio": la política de cupo (cómo se define el periodo, qué cuenta como atención incluida, qué
significa `limite = null`) cambia por decisión comercial, y hoy ese cambio obliga a tocar cuatro
clases que cambian por otras razones (venta, atención, consulta, notificaciones). Cohesión y
acoplamiento: `BonoService` tiene 11 dependencias, una de ellas solo para esta regla.

**Impacto y riesgo.**
- Las dos copias que **deciden dinero** (vender un bono vs. cubrir la atención con el plan) deben
  coincidir exactamente. Si una cambia y la otra no, ocurre una de dos cosas: se vende un bono a quien
  aún tenía cupo (cobro indebido) o se cobra como particular una atención que el plan cubría.
- Ya existe divergencia de implementación: `AfiliacionService` cuenta cargando entidades
  (`buscarIncluidasEnPlan(...).size()`) y las otras usan `contarIncluidasEnPlan`. Hoy ambas consultas
  filtran igual (verificado en `PrestacionRepository` L80-103), pero es la clase de divergencia que la
  duplicación facilita.
- Hipótesis (no probada): ninguna prueba compara directamente el cupo que muestra la consulta del plan
  con el que usa la venta de bonos; la coherencia se apoya en que los tres cálculos se mantengan iguales.

**Acción propuesta.** Extraer un componente de dominio `CupoAtencionesIncluidas` con
`consultar(Afiliacion, LocalDate) → Optional<UsoAtencionesIncluidas>` y un *record*
`UsoAtencionesIncluidas(periodo, limite, usadas)` con la semántica de la regla (`ilimitadas()`,
`quedanDisponibles()`, `restantes()`, `siguienteEsLaUltima()`). `BonoService` y `PrestacionService`
lo usan; cada servicio conserva su reacción propia (excepción con mensaje en la venta, cobertura
`PARTICULAR` en la atención). Alcance acotado a la transacción del bono; `AfiliacionService` y
`NotificacionService` quedan como deuda pendiente (sección 7).

**Verificación.** Mismas pruebas antes y después: suite completa (122) + 3 pruebas de caracterización
de los casos de la regla que no estaban cubiertos (mensaje exacto con fin de periodo, atención anterior
a la afiliación, plan ilimitado sin aviso de cupo agotado) + pruebas unitarias del componente nuevo.

**Estado:** seleccionado.

---

### H-02 · Endpoints de bonos repartidos entre dos controladores; `AfiliacionController` poco cohesivo

**ID:** H-02 | **Tipo:** riesgo arquitectónico (cohesión de la capa web)

**Ubicación:**
- `backend/src/main/java/com/isapet/controller/AfiliacionController.java`: `listarBonos` L170, `cotizarBono` L181, `comprarBono` L196; además plan (L66, L81, L96), cancelación (L109, L123, L134), cuotas (L145, L157) y prestaciones (L208, L222).
- `backend/src/main/java/com/isapet/controller/PagoController.java`: `comprarBonoEnLinea` L58 y `pagarCuotaEnLinea` L52.

**Evidencia:**

```java
// AfiliacionController (L37-59): un controlador, cinco servicios
/** F-04, F-05 y F-06: plan, bonos y prestaciones de un afiliado. */
public AfiliacionController(AfiliacionService afiliacionService, BonoService bonoService,
        PrestacionService prestacionService, CuotaPlanService cuotaService,
        CancelacionPlanService cancelacionService) { ... }
```

```java
// PagoController (L35, L58): depende de BonoService solo para un endpoint
public PagoController(PagoService pagoService, BonoService bonoService) { ... }
@PostMapping("/api/v1/afiliados/{afiliadoId}/bonos/en-linea")
public PagoEnLineaResponse comprarBonoEnLinea(...) { return bonoService.comprarEnLinea(afiliadoId, request); }
```

**Observación.** La compra de bonos tiene dos variantes (en ISAPET y en línea) que viven en
controladores distintos; lo mismo ocurre con el pago de cuotas (`/pagos/{cuotaId}` en
`AfiliacionController` y `/pagos/{cuotaId}/en-linea` en `PagoController`). `AfiliacionController`
agrupa por *recurso padre* (el afiliado) cinco funcionalidades que cambian por razones distintas.
No es un defecto: los controladores son delgados y no contienen reglas (verificado).

**Criterio.** SRP a nivel de módulo y *Common Closure Principle*: lo que cambia junto debe estar junto.

**Impacto y riesgo.** Un cambio en F-05 (p. ej., nuevo canal de pago o versión v2 del contrato) toca
dos controladores; la documentación OpenAPI de bonos queda partida; la propiedad del código por
funcionalidad es menos clara. Riesgo bajo, impacto en mantenibilidad (RNF008, modularidad).

**Acción propuesta.** Crear `BonoController` con los cuatro endpoints de bonos (mismas URLs, mismo
contrato) y, análogamente, unificar pagos de cuota. No requiere cambios en el frontend.

**Verificación.** Pruebas MockMvc de las cuatro rutas antes/después (status, cuerpo, seguridad/CSRF).

**Estado:** pendiente (priorizado por debajo de H-01, ver sección 4).

---

### H-03 · Secreto del webhook de la pasarela con valor por defecto conocido

**ID:** H-03 | **Tipo:** riesgo arquitectónico (seguridad, configuración *secure by default*)

**Ubicación:**
- `backend/src/main/java/com/isapet/service/FirmaPasarela.java`, constructor, L24
- `backend/src/main/resources/application.yml`, L48
- `docker-compose.yml`, L82
- `backend/src/main/java/com/isapet/security/SecurityConfiguration.java`, L138 (`permitAll` del webhook)

**Evidencia:**

```java
// FirmaPasarela L24
public FirmaPasarela(@Value("${app.pasarela.secreto:secreto-de-pruebas-isapet}") String secreto) {
```

```yaml
# application.yml L48
    secreto: ${PASARELA_SECRETO:secreto-de-pruebas-isapet}
```

```java
// SecurityConfiguration L138
.requestMatchers(HttpMethod.POST, "/api/v1/pasarela/webhook").permitAll()
```

**Observación.** El webhook es público por diseño (lo llama la pasarela) y su única protección es la
firma HMAC. Si el despliegue no define `PASARELA_SECRETO`, la aplicación arranca **en silencio** con un
secreto que está publicado en un repositorio público. `.env.example` advierte "CAMBIAR en producción",
pero nada lo obliga.

**Criterio.** Seguridad por defecto / *fail-fast* de configuración; RNF005 (solo usuarios autorizados) y
RNF007 (protección de datos).

**Impacto y riesgo.** Hipótesis de explotación (no ejecutada contra un despliegue real): con el secreto
por defecto, cualquiera puede calcular la firma de un evento `APROBADO` con la referencia y el monto de
un pago pendiente (ambos visibles para el afiliado en la sesión de pago) y activar un bono sin pagarlo,
o saldar una cuota. Hoy la pasarela es simulada (`PasarelaSimulada`), lo que reduce el impacto actual,
pero el riesgo se materializa al conectar una real.

Observación relacionada: `.claude/launch.json` (versionado) contiene una contraseña de
*bootstrap* del administrador para desarrollo local. No se reproduce aquí; se recomienda retirarla del
repositorio y rotarla.

**Acción propuesta.** Eliminar los valores por defecto del secreto y fallar al arrancar si falta (salvo
en el perfil de pruebas); retirar credenciales de `launch.json`. Nota: esto **cambia el comportamiento
observable** (el arranque falla sin la variable), así que es una corrección de configuración, no una
refactorización, y requiere acuerdo del equipo.

**Verificación.** Prueba de contexto: sin `app.pasarela.secreto` la aplicación no arranca; con él, la
suite `LibroPagosYPasarelaIntegrationTest` sigue pasando.

**Estado:** pendiente.

---

### H-04 · El bono de valor 0 se persiste antes de rechazar su pago en línea

**ID:** H-04 | **Tipo:** oportunidad de mejora (regla repartida entre dos servicios; validación después de persistir)

**Ubicación:**
- `backend/src/main/java/com/isapet/service/BonoService.java`, `comprarEnLinea`, L128-136
- `backend/src/main/java/com/isapet/service/PagoService.java`, `iniciarPagoBono`, L112-114

**Evidencia:**

```java
// BonoService.comprarEnLinea (L128-136)
Bono bono = bonoRepository.saveAndFlush(new Bono(..., true));   // INSERT + flush
return pagoService.iniciarPagoBono(bono);
```

```java
// PagoService.iniciarPagoBono (L112-114)
if (bono.getValorPagado().signum() == 0) {
    throw new BusinessRuleException("El bono no tiene valor a pagar; regístralo como compra en ISAPET.");
}
```

**Observación.** Hecho: `BusinessRuleException` extiende `RuntimeException`, así que la transacción de
`comprarEnLinea` se revierte y la base queda consistente; **no es un defecto funcional**. Pero la
decisión "este bono no se puede pagar en línea" vive en el libro de pagos y se toma después de insertar
el bono, de modo que `cotizar()` no puede anticiparla (la cotización responde 200 y la compra en línea
responde 422). Hipótesis: como el id es `IDENTITY` y las secuencias de PostgreSQL no son
transaccionales, cada intento rechazado consume un id y deja un hueco en los códigos `BN-xxxxxx`.

**Criterio.** Reglas de negocio en el componente que toma la decisión; validar antes de persistir.

**Impacto y riesgo.** Latente: ningún beneficio sembrado tiene 100 % de descuento (`V5__...sql`
L98-115), aunque la restricción de la base lo permite (`CHECK ... BETWEEN 0 AND 100`) y un
administrador podría configurarlo.

**Acción propuesta.** Comprobar el valor a pagar antes de `saveAndFlush`, como parte de la elegibilidad
del canal en línea, y exponerlo en la cotización.

**Verificación.** Prueba de integración: con un beneficio al 100 %, `comprarEnLinea` responde 422 y no
existe ningún bono nuevo ni se consume id (comparar `max(id)` antes y después).

**Estado:** pendiente.

---

### H-05 · Validación "la mascota pertenece al afiliado" copiada en tres servicios

**ID:** H-05 | **Tipo:** oportunidad de mejora (duplicación menor)

**Ubicación:** `BonoService.evaluar` L150-154; `PrestacionService.mascotaDelAfiliado` L183-190;
`AfiliacionService.mascotasDelAfiliado` L203-213 (variante para listas).

**Evidencia:** las tres lanzan exactamente
`new BusinessRuleException("La mascota con id " + mascotaId + " no pertenece al afiliado.")` tras
comparar `mascota.getAfiliado().getId()` con `afiliadoId`.

**Observación / criterio.** Invariante de pertenencia (RF006/RF013: el afiliado solo accede a sus
mascotas) repetida; candidata a un método de dominio (`Mascota.perteneceA(afiliadoId)`) o a un único
método de búsqueda en `MascotaService`. Impacto bajo: hoy las tres copias son idénticas.

**Verificación.** Las pruebas existentes `noPermiteCubrirMascotasDeOtroAfiliado` y
`noAceptaUnBonoDeOtroServicioOMascota` cubren el caso.

**Estado:** pendiente.

---

## 4. Priorización

Escala 1–3 (3 = mayor). Prioridad = impacto + riesgo − esfuerzo.

| ID | Impacto | Riesgo | Esfuerzo | Prioridad | Decisión |
|---|---|---|---|---|---|
| H-01 | 3 | 2 | 2 | **3** | **Se refactoriza** |
| H-03 | 3 | 3 | 1 | 5 | Se reporta al equipo; no se implementa en este taller (cambia comportamiento de arranque: es corrección de configuración, no refactorización) |
| H-02 | 2 | 1 | 2 | 1 | Pendiente |
| H-04 | 1 | 1 | 1 | 1 | Pendiente |
| H-05 | 1 | 1 | 1 | 1 | Pendiente (se puede hacer junto con H-01 en otra iteración) |

**Razón de selección de H-01.**
1. Es la única regla que **decide dinero en dos puntos distintos** de la misma transacción (vender el
   bono / cubrir la atención); su duplicación es el riesgo de mayor impacto dentro de F-05.
2. Se puede corregir **sin cambiar el comportamiento observable** (mismos mensajes, códigos HTTP y
   orden de validaciones), que es lo que exige el taller para una refactorización.
3. Está bien protegida: 19 pruebas de integración de F-03 a F-06 contra PostgreSQL real, más las de
   suspensión/activación; antes de tocar el código se agregan pruebas de caracterización de los casos
   de la regla que no estaban cubiertos (sección 6).

H-03 tiene mayor puntaje bruto, pero corregirlo altera el comportamiento observable (la aplicación
dejaría de arrancar sin la variable) y depende de una decisión del equipo sobre el despliegue; por eso
se escala como hallazgo de seguridad y no como la refactorización del taller.

---

## 5. Intervención

*Se completa en la sesión 2.*

## 6. Verificación

*Se completa en la sesión 2.*

## 7. Cierre

*Se completa en la sesión 2.*

## 8. Git

*Se completa en la sesión 2.*
