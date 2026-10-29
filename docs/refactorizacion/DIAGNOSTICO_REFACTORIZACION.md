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
| H-01 | Deuda técnica (duplicación de regla de dominio, SRP/cohesión) | `BonoService.evaluar` L164-188 · `PrestacionService.cupoIncluidoDisponible` L142-150 | La regla del cupo de atenciones incluidas está reimplementada en 4 servicios | Verificado (en F-05; resto en §7.2) |
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

**Estado:** seleccionado → implementado (`c7cb685`) → verificado (sección 6).

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
repositorio y rotarla. *Actualización:* el archivo ya la lee de `.env` en lugar de tenerla escrita; la
rotación queda a cargo del dueño de la credencial, porque el valor anterior sigue en el historial de Git.

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

**Cambio elegido:** H-01 — *Extract Class* de la regla del cupo de atenciones incluidas. Es una
**refactorización** (no corrige un defecto): no cambia mensajes, códigos HTTP, orden de validaciones
ni datos persistidos.

### 5.1 Diseño antes / después

```
ANTES                                              DESPUÉS
─────                                              ───────
BonoService.evaluar                                BonoService.evaluar
  ├─ BeneficiosPlanResolver.versionVigente           └─ CupoAtencionesIncluidas.consultar(afiliacion, hoy)
  ├─ PeriodoAfiliacion.queContiene                         → UsoAtencionesIncluidas
  ├─ PrestacionRepository.contarIncluidasEnPlan              (ilimitadas, quedanDisponibles, restantes)
  └─ if (usadas < limite) ...
                                                   PrestacionService.cupoIncluidoDisponible
PrestacionService.cupoIncluidoDisponible             └─ CupoAtencionesIncluidas.consultar(afiliacion, fecha)
  ├─ BeneficiosPlanResolver.versionVigente                 → UsoAtencionesIncluidas
  ├─ PeriodoAfiliacion.queContiene                           (quedanDisponibles, siguienteEsLaUltima)
  ├─ PrestacionRepository.contarIncluidasEnPlan
  └─ usadas < limite ? ... usadas + 1 == limite    CupoAtencionesIncluidas  ← ÚNICA fuente de la regla
                                                     ├─ BeneficiosPlanResolver.versionVigente
(la regla escrita dos veces dentro de F-05)          ├─ PeriodoAfiliacion.queContiene
                                                     └─ PrestacionRepository.contarIncluidasEnPlan
```

- `CupoAtencionesIncluidas` (`@Component`, capa de servicio) **calcula**: periodo que contiene la fecha,
  versión vigente, límite y atenciones usadas. Vacío si no hay versión vigente.
- `UsoAtencionesIncluidas` (*record* inmutable) **interpreta** la regla: `ilimitadas()`,
  `quedanDisponibles()`, `restantes()`, `siguienteEsLaUltima()`. Antes esta semántica estaba
  implícita en `limite == null`, `usadas < limite` y `usadas + 1 == limite` repetidos.
- Cada caso de uso conserva **su reacción**, que sí es propia: la venta lanza
  `BusinessRuleException` con su mensaje; la atención cae a cobertura `PARTICULAR` y publica el aviso de
  cupo agotado. Las guardas específicas (plan pendiente de activación, suspendido, cancelación
  cumplida, fecha anterior a la afiliación) quedan donde estaban, porque difieren entre los dos casos de
  uso por diseño.

Fragmento después (`BonoService.evaluar`, L162-183 en `c7cb685`):

```java
LocalDate hoy = LocalDate.now(clock);
UsoAtencionesIncluidas uso = cupoAtenciones.consultar(afiliacion, hoy)
        .orElseThrow(() -> new BusinessRuleException(
                "El plan del afiliado no tiene condiciones comerciales vigentes a la fecha."));
... // pendiente de activación y suspensión: sin cambios
if (uso.ilimitadas()) { throw ... "incluye atenciones ilimitadas" ... }
if (uso.quedanDisponibles()) {
    throw ... .formatted(uso.restantes(), uso.limite(), uso.periodo().hasta()));
}
```

Fragmento después (`PrestacionService.cupoIncluidoDisponible`, L142-144 en `c7cb685`):

```java
return cupoAtenciones.consultar(afiliacion, fecha)
        .filter(UsoAtencionesIncluidas::quedanDisponibles)
        .map(uso -> new CupoIncluido(uso.periodo(), uso.siguienteEsLaUltima()));
```

### 5.2 Archivos afectados

| Archivo | Cambio |
|---|---|
| `backend/src/main/java/com/isapet/service/CupoAtencionesIncluidas.java` | **Nuevo**. Cálculo del cupo del periodo |
| `backend/src/main/java/com/isapet/service/UsoAtencionesIncluidas.java` | **Nuevo**. *Record* con la semántica de la regla |
| `backend/src/main/java/com/isapet/service/BonoService.java` | Usa el componente; deja de depender de `PrestacionRepository` y de `VersionPlan` |
| `backend/src/main/java/com/isapet/service/PrestacionService.java` | Usa el componente; deja de depender de `BeneficiosPlanResolver` |
| `backend/src/test/java/com/isapet/service/CupoAtencionesIncluidasTest.java` | **Nuevo**. 5 pruebas unitarias del componente |
| `backend/src/test/java/com/isapet/integration/AfiliacionBonosPrestacionesIntegrationTest.java` | +3 pruebas de caracterización (commit previo al refactor) |

Diff completo del código de producción: `evidencias/05-diff-refactorizacion.diff`
(5 archivos, +213 / −27, de los cuales 131 líneas son la prueba unitaria nueva).

### 5.3 Diferencias internas declaradas (no observables)

Se dejan explícitas para que la revisión no tenga que descubrirlas:
1. En `BonoService.evaluar`, la fecha de hoy se toma **una vez** (`hoy`) en lugar de tres llamadas a
   `LocalDate.now(clock)`. Antes, una compra hecha justo a medianoche podía evaluar suspensión y periodo
   con fechas distintas; ahora es consistente. Ninguna prueba ni contrato dependía de eso.
2. En `BonoService.evaluar`, el conteo de prestaciones del periodo se ejecuta al consultar el cupo, es
   decir, **antes** de las guardas de activación y suspensión (antes se ejecutaba después). Es una
   consulta de solo lectura dentro de la misma transacción: no cambia resultados, mensajes ni datos; solo
   agrega una lectura en los caminos que terminan rechazando la compra.

---

## 6. Verificación

### 6.1 Estrategia

1. **Línea base** sobre `main` sin cambios: suite completa del backend.
2. **Pruebas de caracterización** escritas *antes* del refactor y ejecutadas sobre el código original,
   para fijar los casos de la regla que no estaban cubiertos:
   - `elRechazoPorAtencionesDisponiblesIndicaCuantasQuedanYHastaCuando`: mensaje **exacto**, incluido
     el último día del periodo.
   - `lasAtencionesAnterioresALaAfiliacionSonParticularesYNoConsumenElCupo`.
   - `conAtencionesIlimitadasTodasSonDelPlanYNuncaSeAvisaCupoAgotado`: 5 atenciones `PLAN` y ningún
     evento `AtencionesIncluidasAgotadas`.
3. **Refactor** y nueva ejecución de la suite completa, sin modificar ninguna prueba existente.
4. **Pruebas unitarias** del componente nuevo (`CupoAtencionesIncluidasTest`): sin versión vigente,
   ilimitado (no consulta la base), con cupo, última atención y cupo agotado, con un periodo que no
   empieza el día 1 (afiliación el 20 de enero, fecha 5 de marzo → periodo 20/02–19/03).
5. **Prueba de mutación** manual: se rompe la regla a propósito para comprobar que las pruebas la
   protegen de verdad.

Entorno: `docker compose --profile tests run --rm backend-tests` (Maven 3.9.9, Temurin 17,
PostgreSQL 16-alpine, base `isapet_test` exclusiva de pruebas; cada prueba de integración revierte su
transacción).

### 6.2 Resultados

| Momento | Código | Pruebas | Fallos | Errores | Resultado | Evidencia |
|---|---|---|---|---|---|---|
| Antes (línea base) | `3313321` | 122 | 0 | 0 | BUILD SUCCESS | `evidencias/01-pruebas-antes.txt` |
| Caracterización sobre código original | `3ff2a71` | 22 (clase F-03..F-06) | 0 | 0 | BUILD SUCCESS | `evidencias/02-caracterizacion-antes.txt` |
| Después del refactor | `c7cb685` | 130 (= 122 + 3 + 5) | 0 | 0 | BUILD SUCCESS | `evidencias/03-pruebas-despues.txt` |
| Mutación `usadas < limite` → `usadas <= limite` (no versionada) | `c7cb685` + mutación | 39 | 2 | 5 | BUILD FAILURE (esperado) | `evidencias/04-mutacion-detectada.txt` |

Lectura de la mutación: al permitir "una atención de más", fallan 7 pruebas —entre ellas
`lasAtencionesSinBonoUsanLasIncluidasYDespuesSonParticulares` (la tercera atención dejaría de ser
`PARTICULAR`) y `conElCupoAgotadoNoQuedanDisponibles`—, lo que demuestra que la regla extraída está
protegida tanto por las pruebas de integración como por las unitarias. El archivo se restauró y la
suite volvió a pasar completa.

### 6.3 Limitaciones

- No se ejecutaron las pruebas del frontend (Karma requiere Chrome y Node 20 en el equipo). No hacen
  falta para este cambio: el frontend no se modificó y el contrato de la API es idéntico (mismas rutas,
  cuerpos y mensajes, verificado por las pruebas de integración y MockMvc del backend).
- No se hizo prueba manual de la interfaz ni pruebas de concurrencia. El bloqueo pesimista sobre
  `afiliacion` (que serializa compra de bono y registro de atención) **no se tocó**: sigue en
  `BonoService.comprar`/`comprarEnLinea` y en `PrestacionService.cupoIncluidoDisponible`.
- `AfiliacionService` y `NotificacionService` siguen con su propia copia del cálculo (fuera del alcance
  acotado del taller; ver sección 7).

---

## 7. Cierre

### 7.1 Lecciones

- **Una regla que decide dinero en dos puntos debe tener un solo dueño.** El código de F-05 estaba bien
  escrito y bien probado, y aun así la regla central vivía en dos métodos privados; ninguna prueba
  hubiera fallado si alguien cambiaba solo uno.
- **Caracterizar antes de mover.** Las pruebas existentes cubrían los casos felices y la mayoría de
  rechazos, pero no el texto exacto del mensaje ni el caso de atención anterior a la afiliación; fijarlos
  primero convirtió el refactor en un cambio verificable, no en uno "que parece igual".
- **No todo hallazgo se arregla refactorizando.** H-03 (secreto por defecto) es más grave que H-01,
  pero corregirlo cambia el comportamiento de arranque: es una decisión de despliegue del equipo.
- **La IA acelera el rastreo, no reemplaza la verificación.** Varias afirmaciones candidatas se
  descartaron o se degradaron a hipótesis al verificarlas (p. ej., H-04 no es un defecto porque la
  transacción sí se revierte; que `BonoService` no revise `fechaFin` no es un hueco porque
  `RenovacionPeriodosJob` → `CancelacionPlanService.aplicarCancelacionesCumplidas` elimina la
  afiliación al día siguiente de `fechaFin`; solo queda la ventana entre medianoche y las 00:05, hora
  del *job*, como hipótesis menor no probada).

### 7.2 Deuda técnica pendiente

| ID | Deuda | Siguiente paso sugerido |
|---|---|---|
| H-01 (resto) | `AfiliacionService.response` (L226-272) y `NotificacionService.recordarFinDePeriodo` (L263-280) aún calculan el cupo por su cuenta | Migrarlos a `CupoAtencionesIncluidas`; en `AfiliacionService` el listado de prestaciones incluidas se sigue necesitando para la respuesta, solo el saldo debería salir del componente |
| H-02 | Endpoints de bonos y de pago de cuotas repartidos entre `AfiliacionController` y `PagoController` | `BonoController` con las mismas URLs |
| H-03 | Secreto del webhook con valor por defecto; credencial de desarrollo que estuvo en `.claude/launch.json` (ya retirada del archivo, sigue en el historial) | *Fail-fast* sin `PASARELA_SECRETO` fuera del perfil de pruebas; rotar la credencial |
| H-04 | Bono de valor 0 insertado antes de rechazar el pago en línea | Validar antes de persistir y exponerlo en la cotización |
| H-05 | Validación de pertenencia mascota–afiliado en 3 servicios | Método de dominio o búsqueda única |
| — | `fix_backend.py` y `refactor_backend.py` en la raíz: migración hexagonal no aplicada, con rutas absolutas de un equipo | Decidir como equipo si la arquitectura objetivo es hexagonal (y documentarla en un ADR) o eliminar los scripts |

### 7.3 Vínculo con el proceso de desarrollo

**Pregunta de cierre: ¿qué práctica habría detectado estos problemas antes de entregar?**

1. **Revisión de código con lista de verificación en cada *pull request*.** El historial muestra
   integraciones grandes (p. ej., `3313321` "pagos, notificaciones, ciclo de vida del plan, inicio y
   mejoras de usabilidad" y `90c5569` "implementar F-03 a F-06") y un solo *pull request* registrado (#1). Un PR por
   funcionalidad, revisado con la lista de la sección 3 del taller ("¿hay duplicación?", "¿dónde se
   aplican las invariantes?"), habría señalado H-01 al aparecer la segunda copia del cálculo de cupo, y
   H-03 al ver un secreto con valor por defecto.
2. **Registrar la arquitectura y las decisiones (ADR).** Sin un documento de capas, nadie podía
   contrastar la implementación; tampoco quedó registrado si la migración hexagonal de los scripts se
   abandonó o sigue pendiente.
3. **Análisis estático en CI** (p. ej., detección de duplicados y de secretos en el pipeline) habría
   marcado H-01/H-05 y H-03 automáticamente.
4. **Definición de "terminado" que incluya refactorizar** (ciclo rojo-verde-refactor de TDD): las
   pruebas ya existían; faltó el paso de refactorizar una vez en verde.

---

## 8. Git

Rama de trabajo: `taller-refactorizacion` (desde `main` @ `3313321`).

| # | Commit | Mensaje | Contenido |
|---|---|---|---|
| 1 | [`da6ce83`](https://github.com/hpadillas/isapet/commit/da6ce83) | `docs: registrar diagnostico tecnico` | Secciones 1–4 del informe + `evidencias/01-pruebas-antes.txt` |
| 2 | [`3ff2a71`](https://github.com/hpadillas/isapet/commit/3ff2a71) | `test: caracterizar la regla de cupo antes de refactorizar` | 3 pruebas de caracterización + `evidencias/02-caracterizacion-antes.txt` |
| 3 | [`c7cb685`](https://github.com/hpadillas/isapet/commit/c7cb685) | `refactor: separar responsabilidades de compra de bonos` | `CupoAtencionesIncluidas`, `UsoAtencionesIncluidas`, cambios en `BonoService` y `PrestacionService`, prueba unitaria |
| 4 | [`15b6987`](https://github.com/hpadillas/isapet/commit/15b6987) | `docs: documentar verificacion de refactorizacion` | Secciones 5–8 + `evidencias/03`, `04`, `05` |

La rama se integró a `main` con el *pull request*
[#2](https://github.com/hpadillas/isapet/pull/2) (merge `47ee84a`).

**Cómo reproducir la verificación** (desde la raíz, con Docker Desktop iniciado y un `.env` creado a
partir de `.env.example`):

```powershell
docker compose --profile tests run --rm backend-tests
```

---

## 9. Preguntas de sustentación (refactorización H-01)

### 9.1 ¿Cuál era el problema identificado y dónde estaba localizado?

La regla del **cupo de atenciones incluidas del plan** (cuántas atenciones cubre el plan en el periodo y
cuántas quedan) estaba **escrita dos veces** dentro de la compra de bonos (F-05):

- `BonoService.evaluar` (L164-188): decide si se puede vender un bono.
- `PrestacionService.cupoIncluidoDisponible` (L142-150): decide si una atención la paga el plan o es
  particular.

Ambas copias repetían a mano los mismos cuatro pasos: periodo vigente → versión del plan → límite de
atenciones → conteo de las ya usadas. Existían además otras dos copias en `AfiliacionService` y
`NotificacionService`, que quedaron como deuda técnica pendiente (§7.2).

**Riesgo:** las dos copias **deciden dinero**. Si se modificaba una y no la otra, el sistema podía venderle
un bono a un afiliado que aún tenía cupo, o cobrar como particular una atención que el plan cubría.

### 9.2 ¿Qué principio de diseño o atributo de calidad estaba comprometido?

- **DRY / única fuente de verdad:** una misma regla de negocio vivía en varios lugares.
- **SRP (una sola razón de cambio):** la regla del cupo cambia por decisiones comerciales, pero modificarla
  obligaba a tocar servicios que cambian por otras razones (vender bonos, registrar atenciones).
- **Cohesión y acoplamiento:** `BonoService` dependía de `PrestacionRepository` solo para contar
  atenciones, un detalle de persistencia de otro módulo.
- **Atributo de calidad afectado:** **mantenibilidad**, en particular la modificabilidad y la modularidad.

### 9.3 ¿Qué modificación realizaron y cómo cambiaron las responsabilidades o dependencias?

Se aplicó la refactorización ***Extract Class***, creando dos clases:

- **`CupoAtencionesIncluidas`** (`@Component`): **calcula** el cupo — periodo que contiene la fecha, versión
  vigente, límite y atenciones usadas.
- **`UsoAtencionesIncluidas`** (*record* inmutable): **interpreta** el resultado con métodos expresivos:
  `ilimitadas()`, `quedanDisponibles()`, `restantes()`, `siguienteEsLaUltima()`. Antes esa semántica estaba
  implícita en `limite == null`, `usadas < limite` y `usadas + 1 == limite`.

| | Antes | Después |
|---|---|---|
| `BonoService` | Calculaba el cupo; dependía de `PrestacionRepository` y `VersionPlan` | Solo consulta el cupo y decide si vende; ya no depende de `PrestacionRepository` |
| `PrestacionService` | Calculaba el cupo; dependía de `BeneficiosPlanResolver` | Solo consulta el cupo y decide si la atención es del plan o particular |
| Regla del cupo | Duplicada | Vive únicamente en `CupoAtencionesIncluidas` |

Cada servicio conserva su **reacción propia**: la venta rechaza la compra con su mensaje de error; la
atención pasa a cobertura particular y publica el aviso de cupo agotado.

### 9.4 ¿Qué consecuencias tiene el cambio sobre la arquitectura de la aplicación?

- **Se respeta la arquitectura en capas** (controlador → servicio → repositorio): no cambiaron URLs,
  mensajes ni códigos HTTP, y el frontend no se modificó.
- **La regla de dominio tiene un único dueño:** la venta de bonos y el registro de atenciones ya no pueden
  contradecirse.
- **Menor acoplamiento:** el acceso a las prestaciones queda encapsulado en el componente de cupo, en lugar
  de que `BonoService` conozca ese repositorio.
- **Mejor capacidad de prueba:** la regla se prueba de forma aislada con pruebas unitarias, sin base de datos.
- **Facilita la evolución:** si el negocio redefine el periodo o qué cuenta como atención incluida, se
  modifica una sola clase.
- **Límite declarado:** `AfiliacionService` y `NotificacionService` aún conservan su propia copia; el
  siguiente paso es migrarlos al mismo componente.

### 9.5 ¿Qué evidencias demuestran que la funcionalidad conserva su comportamiento?

| Momento | Commit | Pruebas | Resultado | Evidencia |
|---|---|---|---|---|
| Línea base (código original) | `3313321` | 122 | 0 fallos · BUILD SUCCESS | `evidencias/01-pruebas-antes.txt` |
| Caracterización sobre código original | `3ff2a71` | 22 (clase F-03..F-06) | 0 fallos · BUILD SUCCESS | `evidencias/02-caracterizacion-antes.txt` |
| Después del refactor | `c7cb685` | 130 (122 + 3 + 5) | 0 fallos · BUILD SUCCESS | `evidencias/03-pruebas-despues.txt` |
| Mutación `<` → `<=` | `c7cb685` + mutación | 39 | 7 fallan (esperado) | `evidencias/04-mutacion-detectada.txt` |

1. **Línea base:** las 122 pruebas pasan sobre el código original.
2. **Pruebas de caracterización escritas antes de refactorizar:** fijan casos no cubiertos — el mensaje
   exacto de rechazo con la fecha de fin del periodo, las atenciones anteriores a la afiliación y el plan
   ilimitado sin aviso de cupo agotado.
3. **Después del refactor pasan las 130 pruebas**, sin modificar ninguna prueba existente.
4. **Prueba de mutación:** al romper la regla a propósito fallan 7 pruebas, lo que demuestra que las
   pruebas realmente la protegen.
5. **Trazabilidad en Git:** diagnóstico `da6ce83` → caracterización `3ff2a71` → refactor `c7cb685` →
   verificación `15b6987`; diff completo en `evidencias/05-diff-refactorizacion.diff`.

Las pruebas de integración se ejecutan contra PostgreSQL real, en una base exclusiva de pruebas:
`docker compose --profile tests run --rm backend-tests`.
