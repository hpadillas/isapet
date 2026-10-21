# ISAPET

Sistema web para la gestión de afiliados de una entidad de salud para mascotas.

## Estructura

- `backend`: API REST con Spring Boot 3.4.5, Java 17 y Maven.
- `frontend`: aplicación Angular 18 y TypeScript.
- `docs`: documentación, trazabilidad y evidencias.
- `docker-compose.yml`: aplicación completa (PostgreSQL, backend Spring Boot y frontend Angular con Nginx).

F-01 permite registrar afiliados y agregar cero o varias mascotas relacionadas con cada afiliado. La Gestión de Afiliados permite consultar, buscar, editar y administrar sus mascotas.

## Ejecución con Docker (recomendado)

Solo se necesita **Docker Desktop** (en Windows, con el backend WSL 2) y **Git**. No hace falta instalar Java, Maven, Node.js, Angular ni PostgreSQL: todo se compila y ejecuta dentro de los contenedores.

| Servicio | Contenedor | Función | Acceso |
|---|---|---|---|
| `frontend` | `isapet-frontend` | Nginx sirve Angular compilado y reenvía `/api` al backend | `http://localhost:8080` (`APP_PORT`) |
| `backend` | `isapet-backend` | API Spring Boot (perfil `docker`) | Solo red interna; a través de Nginx en `/api` |
| `postgres` | `isapet-postgres` | PostgreSQL 16 con volumen persistente `isapet_isapet-postgres-data` | `127.0.0.1:5433` (`POSTGRES_HOST_PORT`), solo desde el propio equipo |

El navegador solo habla con Nginx: la interfaz y la API comparten origen, por lo que no se necesita CORS y las cookies de sesión y el token CSRF funcionan sin configuración adicional. El backend se conecta a la base por el nombre de servicio `postgres:5432` dentro de la red `isapet-net`.

### 1. Clonar el repositorio

```powershell
git clone https://github.com/Ferneycardona14/isapet.git
cd isapet
```

### 2. Crear el archivo `.env`

```powershell
Copy-Item .env.example .env
```

(En macOS/Linux: `cp .env.example .env`.) Abre `.env` y cambia como mínimo:

- `POSTGRES_PASSWORD`: clave de la base de datos.
- `ADMIN_USERNAME` y `ADMIN_PASSWORD`: credenciales del administrador inicial (la clave admite hasta 72 bytes).

`.env` está en `.gitignore` y no debe subirse al repositorio. Docker Compose no arranca si faltan `POSTGRES_PASSWORD`, `ADMIN_USERNAME` o `ADMIN_PASSWORD`.

### 3. Administrador inicial

Al arrancar, el backend crea el usuario de `ADMIN_USERNAME` con rol `ADMIN` y la clave cifrada con BCrypt, **solo si aún no existe ningún administrador**. Los reinicios no crean duplicados y nunca cambian la clave de un administrador existente: cambiar `ADMIN_PASSWORD` en `.env` después del primer arranque no tiene efecto (ver solución de problemas). Una vez creado puedes poner `BOOTSTRAP_ADMIN_ENABLED=false`.

### 4. Levantar la aplicación

```powershell
docker compose up --build
```

La primera vez tarda varios minutos (descarga imágenes y dependencias). Cuando los servicios estén `healthy`, abre `http://localhost:8080`, entra con **Iniciar sesión** y usa las credenciales del administrador. El simulador de planes (`/simulador`) es público. Swagger queda en `http://localhost:8080/swagger-ui/index.html` y los correos de notificación llegan a Mailpit en `http://localhost:8025`.

### Comandos habituales

| Acción | Comando |
|---|---|
| Levantar en segundo plano | `docker compose up --build -d` |
| Estado y salud de los contenedores | `docker compose ps` |
| Logs de todos los servicios | `docker compose logs -f` |
| Logs de un servicio | `docker compose logs -f backend` (o `frontend`, `postgres`) |
| Detener **conservando los datos** | `docker compose stop` (reanudar con `docker compose start`) |
| Detener y quitar contenedores **conservando los datos** | `docker compose down` |
| Reconstruir imágenes tras cambiar código | `docker compose up --build -d` |
| Reconstruir sin caché | `docker compose build --no-cache` y luego `docker compose up -d` |
| Reiniciar un servicio | `docker compose restart backend` |

### Pruebas

Pruebas del backend (unitarias y de integración contra el PostgreSQL del stack), sin Java ni Maven en el anfitrión:

```powershell
docker compose --profile tests run --rm backend-tests
```

Las pruebas de integración usan una base de datos propia (`isapet_test`, en el mismo PostgreSQL), que se crea sola la primera vez; así no dependen de los datos de la demo ni los modifican, y además revierten sus cambios al terminar. Las pruebas del frontend usan Karma con Chrome, por lo que requieren Node.js 20 y Google Chrome en el equipo:

```powershell
cd frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
```

### Instalación limpia

`docker compose down` **no** borra datos. Para empezar desde cero (base de datos vacía y administrador nuevo según `.env`):

```powershell
docker compose down -v   # ¡ELIMINA el volumen con todos los datos de PostgreSQL!
docker compose up --build
```

### Solución de problemas

- **`port is already allocated` / el puerto 8080 o 5433 está ocupado**: cambia `APP_PORT` o `POSTGRES_HOST_PORT` en `.env` y vuelve a ejecutar `docker compose up -d`.
- **`required variable POSTGRES_PASSWORD is missing`** (o `ADMIN_*`): falta el archivo `.env` o la variable; créalo desde `.env.example`.
- **El backend no arranca con `password authentication failed`**: la base se creó con otra clave. PostgreSQL solo aplica `POSTGRES_PASSWORD` al crear el volumen. Restablece la clave anterior en `.env` o haz una instalación limpia (borra los datos).
- **El backend se detiene con `El bootstrap de ADMIN requiere usuario y contraseña`**: revisa `ADMIN_USERNAME`/`ADMIN_PASSWORD` (máximo 72 bytes).
- **No puedo iniciar sesión con la clave de `.env`**: el administrador ya existía con otra clave y el bootstrap no la reemplaza. Usa la clave original o haz una instalación limpia.
- **Inicio de sesión que "no se mantiene" por HTTP**: en la demo local `SESSION_COOKIE_SECURE` debe ser `false`; con `true` el navegador solo envía la cookie por HTTPS.
- **El frontend muestra errores de conexión justo al arrancar**: espera a que `docker compose ps` muestre `backend` como `healthy` (el backend aplica las migraciones de Flyway al iniciar).
- **Docker Desktop en Windows**: verifica que esté iniciado y en modo WSL 2; si la compilación falla por memoria, asigna al menos 4 GB en *Settings → Resources*.

## Desarrollo local sin Docker para backend y frontend

El `docker-compose.yml` también sirve para tener solo la base de datos durante el desarrollo (requiere el archivo `.env`).

### PostgreSQL local

Requisitos: Docker Desktop iniciado.

Desde la raíz del repositorio, inicia PostgreSQL:

```powershell
docker compose up -d postgres
docker compose ps
```

La base de datos del proyecto está disponible en `localhost:5433` (el puerto interno del contenedor es `5432`). La configuración local usa la base, el usuario y la clave definidos en `.env` (por defecto el backend local espera `isapet`/`isapet`; ajusta `DB_USERNAME`/`DB_PASSWORD` si usas otros). El volumen `isapet-postgres-data` conserva los datos entre reinicios.

Flyway aplica las migraciones pendientes al iniciar el backend.

### Backend y pruebas

Requisitos: Java 17 y Maven. Para el inicio de sesión por HTTP local exporta `SESSION_COOKIE_SECURE=false`, `FRONTEND_ORIGIN=http://localhost:4200` y las variables `BOOTSTRAP_ADMIN_*`.

Desde la raíz del repositorio, ejecuta el backend:

```powershell
Push-Location backend
mvn spring-boot:run
Pop-Location
```

Para ejecutar las pruebas:

```powershell
Push-Location backend
mvn test
Pop-Location
```

## Gestión de Afiliados

Con backend, PostgreSQL y frontend activos, abre `http://localhost:4200/afiliados` para listar y buscar afiliados. Desde el listado se puede abrir el detalle, editar datos de contacto y consultar/agregar/editar mascotas. El formulario de alta de afiliado continúa en `http://localhost:4200/afiliados/registro`.

Swagger/OpenAPI está disponible en `http://localhost:8080/swagger-ui/index.html`.

## Periodos y notificaciones

Las atenciones incluidas se cuentan por **periodo de afiliación**: un mes a partir de la fecha de inicio del plan (afiliado el 20 de octubre, los periodos van del 20 al 19 del mes siguiente). Cambiar de plan reinicia la fecha de inicio. La zona horaria del negocio es `America/Bogota` (`APP_TIMEZONE`).

El backend envía un correo al afiliado (a su `correo`) cuando:

| Aviso | Momento |
|---|---|
| Bienvenida | Se registra el afiliado |
| Plan activado | Se asigna un plan o se cambia por otro (cambiar solo las mascotas no avisa) |
| Atenciones incluidas agotadas | Se registra la atención que usa la última incluida del periodo |
| Fin de periodo | Faltan 2 días o menos para que termine el periodo; la revisión corre a diario a las 8:00 (`RECORDATORIO_CRON`) |

Los correos se envían después de confirmar la operación y en segundo plano, así que un fallo del correo no afecta el registro. Cada envío (o fallo) queda en la tabla `notificacion` (migración V7), que además evita avisos duplicados.

- En la configuración por defecto los correos van a **Mailpit**, una bandeja de prueba que no envía nada a Internet: `http://localhost:8025`.
- Para correo real, configura en `.env` las variables `MAIL_*` (hay un ejemplo con Gmail en `.env.example`) y ejecuta `docker compose up -d`.
- `GET /api/v1/afiliados/{id}/notificaciones`: historial de notificaciones del afiliado.
- `POST /api/v1/admin/notificaciones/recordatorio-fin-periodo?fecha=YYYY-MM-DD`: ejecuta la revisión de fin de periodo sin esperar a las 8:00; `fecha` permite simular otro día.

## Cambio de plan, pagos y eliminación de afiliados

- **Cambio de plan**: elegir otro plan no lo activa de inmediato. El plan actual sigue hasta el último día del periodo en curso y el nuevo se activa al día siguiente, con las mascotas elegidas; mientras tanto el cambio se puede cancelar. Cambiar solo las mascotas del mismo plan aplica de inmediato. La pantalla pide confirmación indicando la fecha final del plan actual y la de activación del nuevo.
- **Pagos**: cada periodo de afiliación genera una cuota pendiente con el valor mensual del plan vigente en esa fecha. El administrador registra el pago (fecha y medio: efectivo, tarjeta o transferencia) en la pestaña **Pagos** del afiliado, que muestra el historial y los totales pagado y pendiente.
- **Activación al pagar** (migración V12): al asignar un plan queda **pendiente de activación** con su cuota de activación; no da atenciones incluidas ni permite comprar bonos. Al pagar esa cuota (en ISAPET o en línea) el plan se activa y su primer periodo empieza el día del pago; ahí se envía el correo de plan activado. La cuota de activación no vence ni suspende; si se cancela sin pagar, se anula. Un plan sin activar no se puede cambiar por otro (se cancela y se asigna el nuevo).
- **Plazo de pago y suspensión**: la cuota se paga desde el inicio del periodo y hasta 4 días después (periodo del 20/11 → hasta el 24/11). Si sigue sin pagar, el plan queda **suspendido**: no se usan atenciones incluidas ni se compran bonos, y el afiliado recibe un correo. Al registrar el pago se reactiva solo y también se avisa por correo.
- **Cancelar plan** (botón en la pestaña Plan): con el periodo actual pagado, el plan sigue hasta su último día y la cancelación se puede deshacer mientras tanto; dentro del plazo de pago sin haber pagado, termina hoy y la cuota se **anula**; si está suspendido, termina hoy y las cuotas vencidas siguen como deuda. Se notifica por correo y se descarta cualquier cambio de plan programado. Los bonos ya comprados se pueden seguir usando.
- **Renovación diaria**: a las 00:05 (y al arrancar el backend) se terminan los planes cancelados, se activan los cambios de plan programados, se generan las cuotas de los periodos que empiezan y se avisan las suspensiones (`RENOVACION_CRON`).
- **Qué incluye**: el detalle de cada plan en `/planes` muestra las atenciones incluidas, el máximo de mascotas, la mensualidad y el descuento en cada servicio.
- **Eliminar afiliado**: desde el listado o la ficha. Antes de confirmar se muestra lo que se borrará de forma permanente: mascotas con su historia clínica (atenciones registradas), bonos, afiliación y pagos; hay que marcar una casilla para confirmar.

Endpoints (rol `ADMIN`):

- `DELETE /api/v1/afiliados/{id}/plan/cambio-programado`: cancela el cambio de plan programado.
- `GET /api/v1/afiliados/{id}/pagos` y `POST /api/v1/afiliados/{id}/pagos/{cuotaId}`: historial y registro de pago.
- `GET`, `POST` y `DELETE /api/v1/afiliados/{id}/plan/cancelacion`: simular, cancelar el plan y deshacer una cancelación programada.
- `GET /api/v1/afiliados/{id}/eliminacion` y `DELETE /api/v1/afiliados/{id}`: resumen previo y eliminación.
- `POST /api/v1/admin/periodos/renovar?fecha=YYYY-MM-DD`: ejecuta la renovación sin esperar a la madrugada; `fecha` permite simular otro día.

La migración V8 agrega el cambio programado a `afiliacion` y crea `cuota_plan`; la V10 agrega la cancelación (`fecha_fin`) y el estado `ANULADA` de las cuotas.

## Pagos, pasarela y conciliación

- **Libro de pagos** (migración V11): cada transacción, sea mensualidad o bono, queda en la tabla `pago` con referencia única, origen (cuota o bono), monto, medio (efectivo, tarjeta, transferencia o PSE), canal (en ISAPET o en línea), estado (pendiente, aprobado, rechazado o anulado) y fechas. Los pagos anteriores se migraron al libro; en los bonos antiguos el medio queda como "no registrado".
- **Pago en ISAPET**: en la pestaña Pagos ("Registrar pago") o al comprar un bono eligiendo el medio. Queda aprobado de inmediato.
- **Pago en línea**: "Pagar en línea" (cuotas) o "Pago en línea" (bonos) abre la pasarela. El pago queda **pendiente** y el bono **pendiente de pago** hasta que la pasarela confirme por el **webhook** `POST /api/v1/pasarela/webhook`, firmado con HMAC-SHA256 (encabezado `X-Firma`, secreto `PASARELA_SECRETO`). Si se aprueba, se salda la cuota o se activa el bono; si se rechaza, la cuota sigue pendiente y el bono se anula. La notificación es idempotente y se rechaza si la firma o el monto no coinciden.
- **Pasarela simulada**: en este entorno la página de pago (`/pasarela/checkout/{referencia}`) es una simulación que permite aprobar o rechazar; el resultado viaja por el mismo webhook firmado. Para una pasarela real (Wompi, PayU, Mercado Pago) se implementa la interfaz `PasarelaPagos`.
- **PCI DSS**: ISAPET nunca pide ni guarda datos de tarjetas; solo registra el medio usado.
- **Finanzas** (`/finanzas`): conciliación por rango de fechas con filtros por estado, concepto, medio y canal, totales por estado y por medio, y exportación a CSV.

Endpoints: `GET /api/v1/afiliados/{id}/transacciones`, `POST /api/v1/afiliados/{id}/pagos/{cuotaId}/en-linea`, `POST /api/v1/afiliados/{id}/bonos/en-linea`, `GET /api/v1/admin/pagos` y los de la pasarela simulada en `/api/v1/admin/pasarela-simulada/{referencia}`.

## Tratamiento de datos personales (Ley 1581 de 2012)

- Al registrar un afiliado se muestra el **aviso de privacidad** (art. 12): responsable y datos de contacto, finalidades, derechos del titular, canal y plazos de atención (consultas en 10 días hábiles, reclamos en 15) y el carácter facultativo de los datos sensibles o de menores. Sin marcar la **autorización previa, expresa e informada** del titular (art. 9) no se puede registrar; el backend también lo valida.
- Se guarda la **prueba de la autorización** (art. 17-b) en `autorizacion_datos`: versión de la política, fecha y usuario que la registró. Se ve en la ficha del afiliado; para afiliados registrados antes de este requisito hay un botón "Registrar autorización".
- La **política completa** es pública en `/privacidad` (`GET /api/v1/privacidad`).
- Configura los datos reales del responsable con `PRIVACIDAD_RESPONSABLE`, `PRIVACIDAD_NIT`, `PRIVACIDAD_DIRECCION`, `PRIVACIDAD_CORREO` y `PRIVACIDAD_TELEFONO` en `.env`; por defecto aparecen como "Por definir". Si cambia el contenido de la política, se sube `PoliticaPrivacidad.VERSION`.

## Seguridad administrativa

La autenticación usa sesiones HTTP y contraseñas BCrypt. La migración Flyway V4 crea las tablas de usuarios y roles e inicializa únicamente el rol `ADMIN`; no contiene cuentas ni credenciales. El primer administrador solo se crea si se habilita explícitamente `BOOTSTRAP_ADMIN_ENABLED` y se proporcionan `BOOTSTRAP_ADMIN_USERNAME` y `BOOTSTRAP_ADMIN_PASSWORD` mediante el entorno o un gestor de secretos. El proceso no reemplaza ni promueve una cuenta existente. Después del bootstrap, deshabilita la opción y retira las credenciales del entorno.

La cookie de sesión es `HttpOnly`, `SameSite=Lax` y `Secure` por defecto. Para desarrollo exclusivamente local sobre HTTP se puede establecer `SESSION_COOKIE_SECURE=false`; en producción debe mantenerse habilitado y servirse la aplicación mediante HTTPS. `FRONTEND_ORIGIN` permite configurar un origen exacto para CORS cuando frontend y backend no comparten origen; no admite comodines con credenciales.

El cliente debe solicitar `GET /api/v1/auth/csrf` antes de iniciar sesión y enviar el valor de la cookie `XSRF-TOKEN` en `X-XSRF-TOKEN` en las operaciones que cambian estado. La autenticación se realiza con `POST /api/v1/auth/login` y JSON `{"username":"…","password":"…"}`; la sesión queda en la cookie `JSESSIONID`. Después del login, vuelve a solicitar el token CSRF antes de otras operaciones mutables. `GET /api/v1/auth/session` devuelve la identidad y roles de la sesión; `POST /api/v1/auth/logout` cierra la sesión y requiere CSRF.

Los prefijos `/api/v1/admin/**`, `/api/v1/afiliados/**` y `/api/v1/mascotas/**` requieren el rol `ADMIN`. La pantalla Angular de login integra la sesión y CSRF; las pantallas F-01 y la administración del catálogo consumen la API con la cookie de sesión. La ruta `/planes` está protegida también por el guard Angular, sin sustituir la autorización del backend. Las sesiones se guardan en memoria del servidor; se invalidan al reiniciar y no se comparten entre varias instancias.

La administración del catálogo está disponible en `/planes`. Permite consultar planes y su historial, crear planes con una primera versión, publicar versiones comerciales inmutables, consultar la versión aplicable a una fecha y desactivar planes sin eliminar su historial. Las versiones vigentes e históricas son inmutables (también lo garantiza un trigger de la base). Una versión programada, que aún no empieza, se puede eliminar: la anterior queda vigente hasta el inicio de la siguiente, y antes de confirmar se avisa cuántos afiliados tienen programado el cambio a ese plan en esas fechas (migración V9). No se puede eliminar la única versión de un plan. Las operaciones utilizan `/api/v1/admin/planes/**` y requieren rol `ADMIN` y CSRF en solicitudes mutables.

- `GET /api/v1/admin/planes`: lista los planes y sus versiones.
- `GET /api/v1/admin/planes/{planId}`: consulta el detalle y el historial.
- `GET /api/v1/admin/planes/{planId}/version-aplicable?fecha=YYYY-MM-DD`: consulta la versión aplicable.
- `POST /api/v1/admin/planes`: crea un plan y su primera versión.
- `POST /api/v1/admin/planes/{planId}/versiones`: publica una nueva versión comercial.
- `PATCH /api/v1/admin/planes/{planId}`: cambia el nombre del plan (no genera versión).
- `GET /api/v1/admin/planes/{planId}/versiones/{versionId}/eliminacion` y `DELETE /api/v1/admin/planes/{planId}/versiones/{versionId}`: impacto y eliminación de una versión programada.
- `PATCH /api/v1/admin/planes/{planId}/desactivar`: desactiva el plan conservando su historial.

Endpoints de administración:

- `GET /api/v1/afiliados?page=0&size=10&q=texto`: listado paginado y búsqueda por identificación, nombres, apellidos o correo. `q` es opcional.
- `GET /api/v1/afiliados/{id}`: detalle del afiliado.
- `PUT /api/v1/afiliados/{id}`: actualiza nombres, apellidos, celular, correo y dirección; la identificación no se modifica.
- `GET /api/v1/afiliados/{id}/mascotas`: lista las mascotas del afiliado.
- `POST /api/v1/afiliados/{id}/mascotas`: agrega mascota al afiliado.
- `PUT /api/v1/mascotas/{mascotaId}`: actualiza los datos de mascota sin cambiar el afiliado propietario.

## Simulación, plan del afiliado, bonos y prestaciones (F-03 a F-06)

La migración V5 crea el catálogo de servicios veterinarios, los descuentos por plan (`beneficio_plan`), la afiliación (plan y mascotas cubiertas de cada afiliado), los bonos y las prestaciones. Los valores de servicios y descuentos sembrados son de referencia y deben validarse con ISAPET.

- **F-03 Simular plan** (`/simulador`, público): compara los planes activos con condiciones vigentes según la cantidad de mascotas y los servicios esperados al mes. Las atenciones incluidas en el plan no tienen costo y las adicionales se calculan con bonos al valor con descuento; se recomienda el plan aplicable de menor costo mensual estimado.
- **F-04 Consultar plan** (`/afiliados/{id}/plan`): muestra el plan del afiliado, sus mascotas cubiertas, descuentos por servicio, el periodo actual, las atenciones incluidas usadas y restantes en él y los bonos del periodo. Desde aquí también se asigna o cambia el plan (no admite planes inactivos ni más mascotas que el máximo del plan).
- **F-05 Comprar bono** (`/afiliados/{id}/bonos`): cotiza y registra bonos con el descuento del plan para atenciones adicionales. Exige plan asignado, mascota cubierta y haber usado todas las atenciones incluidas del periodo actual; después no hay tope. Los planes con atenciones ilimitadas no venden bonos.
- **F-06 Historial de prestaciones** (`/afiliados/{id}/prestaciones`): registra atenciones y las consulta filtrando por mascota. Cada una queda con su cobertura: `BONO` si se indica un bono disponible de la misma mascota y servicio (que queda como usado); `PLAN` si, sin bono, la mascota está cubierta y quedan atenciones incluidas en el periodo de la fecha (sin costo); `PARTICULAR` (valor pleno) en otro caso. La migración V6 agrega la columna `cobertura`.

Endpoints:

- `GET /api/v1/simulaciones/servicios` y `POST /api/v1/simulaciones/planes`: públicos y sin CSRF (no usan sesión ni modifican datos).
- `GET|PUT /api/v1/afiliados/{id}/plan`
- `GET|POST /api/v1/afiliados/{id}/bonos` y `GET /api/v1/afiliados/{id}/bonos/cotizacion?mascotaId=&servicioId=`
- `GET|POST /api/v1/afiliados/{id}/prestaciones` (`GET` admite `mascotaId` opcional)

Las reglas de negocio incumplidas responden `422` con el motivo en `message`. Por ahora estas consultas las realiza el rol `ADMIN`; el acceso del propio afiliado depende de F-08.

Para desarrollo local con `ng serve` y su proxy, el backend necesita `FRONTEND_ORIGIN=http://localhost:4200` (el proxy reenvía la cabecera `Origin` y sin esta variable las peticiones `POST`/`PUT` reciben `403 Invalid CORS request`) y `SESSION_COOKIE_SECURE=false`.

Las pruebas backend se ejecutan con `mvn test` desde `backend`; las pruebas frontend con `npm test -- --watch=false --browsers=ChromeHeadless` desde `frontend`.