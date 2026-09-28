# Arquitectura técnica y ADR

*Java + Spring Boot + React + PostgreSQL, seguridad y despliegue*

- **Documento:** ER-ARCH-001
- **Versión / fecha:** 2.1 · 28 de septiembre de 2026
- **Estado:** decisiones adoptadas e implementadas en el código actual, salvo donde se indica lo contrario. Las
  reglas de negocio que soportan siguen siendo propuestas (ER-BR-001 §0).
- **Responsable:** Jefferson Rojas Brizuela

> Documento vivo. Cambiar una decisión técnica exige un ADR nuevo o una revisión versionada con fecha, problema,
> alternativas, decisión y consecuencias. Los ADR se conservan como se decidieron; una decisión posterior los
> amplía o sustituye en lugar de reescribirlos.

## 1. Decisión ejecutiva y alcance técnico

Monolito modular por funcionalidades con backend Java/Spring Boot y frontend React/TypeScript. PostgreSQL es la fuente de verdad; Flyway controla el esquema y el backend expone una API REST. Se optimiza para un equipo pequeño y una operación sencilla de una sola empresa: una unidad desplegable, una base de datos y transacciones ACID locales.

| Capa | Decisión | Motivo |
|---|---|---|
| Backend | Java 25 LTS + Spring Boot 4.1.1 + Maven Wrapper | LTS reciente y compatibilidad oficial documentada. |
| HTTP | Spring MVC / embedded Tomcat | REST empresarial con modelo servlet familiar. |
| Persistencia | Spring Data JPA + Hibernate + PostgreSQL 17 | Relacional, transacciones y bloqueo concurrente. |
| Schema | Flyway + ddl-auto=validate | Migraciones explícitas revisables. |
| Seguridad | Spring Security + sesión + CSRF SPA | Menor complejidad para web empresarial same-origin. |
| Frontend | React + TypeScript + Vite + React Router | SPA separada con tipado estático y división de código por rutas. |
| Estado servidor | TanStack Query | Caché e invalidación de consultas, sin estado global innecesario. |
| Pruebas | JUnit 5 + Mockito + MockMvc + Testcontainers PostgreSQL | Reglas, HTTP, seguridad y BD real. |
| Operación | Docker Compose local; CI GitHub Actions; futuro Linux/Nginx/TLS | Reproducibilidad y despliegue evolutivo. |

> **NOTA** Compatibilidad comprobada en la documentación oficial: Spring Boot 4.1.1 admite Java 17–26. Java 27 no entra en su rango documentado. Consultar https://docs.spring.io/spring-boot/system-requirements.html al cambiar versiones.

## 2. Arquitectura lógica y carpetas

```text
electronica-rojas/
  docs/                         # 00–05, design-system.md, runbook.md
  backend/                      # Spring Boot (Maven Wrapper): pom.xml, mvnw, mvnw.cmd, .mvn/
    src/main/java/dev/jeffrojas/electronicarojas/
      ElectronicaRojasApplication.java
      security/        # SecurityFilterChain, principal de sesión, CSRF, revocación, límite de login
      users/           # cuentas, roles, administración, /auth/me, bootstrap del primer admin
      branches/        # sucursales y punto único de autorización por sucursal (BranchService)
      inventory/       # catálogo, existencias, StockLedger, movimientos, transferencias, vista consolidada
      customers/       # clientes, teléfonos E.164, búsqueda, consentimiento por canal
      repairs/         # órdenes, RepairPolicy/RepairWorkflow, cotizaciones, repuestos
      servicerequests/ # solicitudes, visitas, agenda, horarios, portal público
      notifications/   # outbox, trabajador, plantillas, transportes de correo, administración
      dashboard/       # indicadores compuestos a partir de los listados
      audit/           # auditoría append-only con detalles estructurados
      shared/          # reloj, ProblemDetail, correlación, paginación, huella de idempotencia, ping
      demo/            # SOLO DESARROLLO: carga del escenario demo con APP_DEMO_SEED=true (ADR-021)
    src/main/resources/application.properties
    src/main/resources/db/migration/   # V1..V12 (inmutables)
    src/test/java/dev/jeffrojas/electronicarojas/   # mismos paquetes
  frontend/                     # React + TypeScript + Vite
    src/app/  src/features/<feature>/  src/shared/  src/styles/
  scripts/                      # start-backend.ps1, check-migrations.sh
  compose.yaml  .env.example  .github/workflows/ci.yml  .vscode/  CLAUDE.md  README.md
```

Dependencias entre módulos, derivadas de los `import` y sin ciclos (además de `shared` y `security`, que no
dependen de ningún módulo): `audit → security`; `branches → audit`; `users → branches`; `customers` e
`inventory → users, branches`; `notifications → customers, branches`; `repairs → customers, inventory
(RepairStock), notifications, users`; `servicerequests → repairs, customers, notifications, users`;
`dashboard → repairs, servicerequests, inventory`. `inventory`, `customers` y `notifications` no conocen a
`repairs` ni a `servicerequests`.

La estructura anterior del proyecto se simplificó al publicar
el repositorio (ADR-020).

## 3. ADR-001: monolito modular frente a microservicios

| Criterio | Monolito modular | Microservicios |
|---|---|---|
| Alcance inicial | Una empresa, pocos procesos y pequeño equipo | Costo de coordinación innecesario. |
| Transacciones inventario | ACID dentro de una DB | Saga/mensajería distribuidas no justificadas. |
| Despliegue | Un backend y un frontend | Múltiples unidades operativas. |
| Decisión | ADOPTADO | DESCARTADO para baseline. |

Los módulos se agrupan por caso de negocio y se comunican por servicios públicos simples; evitar ciclos. Una clase compartida se mueve a shared solo si sirve realmente a varios módulos.

## 4. ADR-002: modelo de identidad y seguridad web

- **ARCH-SEC-001 · MUST** · Spring Security implementa autenticación de sesiones y contraseñas con PasswordEncoder robusto; sin JWT por defecto para un único SPA bajo el mismo dominio.
- **ARCH-SEC-002 · MUST** · Frontend y backend se sirven bajo un mismo origen en producción; en desarrollo Vite usa proxy /api para evitar CORS innecesario.
- **ARCH-SEC-003 · MUST** · Session cookie HttpOnly, Secure con HTTPS, SameSite apropiado; CSRF integrado según guía actual Spring Security para SPA. Al autenticarse/cerrar sesión renovar token CSRF.
- **ARCH-SEC-004 · MUST** · SecurityFilterChain protege todo por defecto; permitir explícitamente solo endpoints públicos aprobados, incluyendo GET /actuator/health con respuesta sanitizada. No abrir /actuator/**.
- **ARCH-SEC-005 · MUST** · Permisos en casos de uso según usuario actual, roles y tabla UserBranch; filtrar además queries para evitar fuga transversal; probar IDOR con IDs de otra sucursal.
- **ARCH-SEC-006 · MUST** · Sesiones in-memory suficientes para demo monoinstancia. Antes de múltiples instancias, evaluar Spring Session/almacenamiento compartido; no implementarlo preventivamente.

## 5. ADR-003: PostgreSQL, transacciones y concurrencia

- **ARCH-DB-001 · MUST** · PostgreSQL 17 dedicado a la aplicación; tabla de stock única por (branch_id,product_id), CHECK quantity >= 0, FKs e índices según búsqueda.
- **ARCH-DB-002 · MUST** · @Transactional en transferencias; bloquear filas implicadas en orden global consistente mediante PESSIMISTIC_WRITE / SELECT FOR UPDATE o actualización condicional con verificación de filas afectadas.
- **ARCH-DB-003 · MUST** · Una transferencia incluye cabecera con UUID operationId e instantáneo UTC y dos movimientos relacionados; UNIQUE operationId; misma clave con payload divergente es conflicto.
- **ARCH-DB-004 · MUST** · Cuando el stock destino todavía no existe, crear fila segura frente a carreras mediante constraint y operación upsert/estrategia verificada con PostgreSQL; no asumir que @Transactional resuelve duplicación.
- **ARCH-DB-005 · MUST** · Ordenar locks por (branch_id,product_id), mantener transacciones cortas y probar deadlocks/retries controlados; stock nunca negativo.
- **ARCH-DB-006 · MUST** · Flyway controla evolución; V1 baseline mínima en Fase 0; migraciones posteriores inmutables una vez aplicadas; Hibernate validate únicamente.

## 6. Modelo de datos

### 6.1 Esquema actual (V1–V12)

| Migración | Contenido |
|---|---|
| V1 `baseline` | Esquema vacío gestionado por Flyway. |
| V2 `identity_and_branches` | `branches`, `app_users`, `user_branches`. |
| V3 `inventory` | `products`, `branch_stock` (UNIQUE sucursal/producto, CHECK cantidad ≥ 0), `stock_transfers`, `stock_movements`; función `reject_history_change()` y triggers append-only. |
| V4 `audit_events` / V5 `audit_details` | `audit_events` append-only; `details JSONB`. |
| V6 `customers_and_repairs` | `customers`, `repair_orders`, `repair_status_history`, `repair_quotes` (índice único parcial «una pendiente»). |
| V7 `repair_orders_no_delete` | Trigger que impide borrar órdenes (ver ADR-012). |
| V8 `customer_search` | `customers.search_name` e índices GIN `pg_trgm`. |
| V9 `home_service` | `service_requests`, `service_visits` (restricción `EXCLUDE` con `btree_gist`), `technician_shifts`, `branch_service_settings`, `service_request_events`. |
| V10 `repair_parts` | `stock_movements.repair_order_id`, `repair_part_usages`, `repair_part_returns`. |
| V11 `consents_and_notifications` | `customer_consents`, consentimientos del formulario público, `notification_outbox`, `notification_attempts`. |
| V12 `pricing_and_public_portal` | Costo y precio en `products`, instantánea de precio en `repair_part_usages`, `public_portal_settings`, `public_portal_slug_history`. |

Todas las claves primarias son `BIGINT GENERATED ALWAYS AS IDENTITY` (ADR-007). El detalle de cada decisión está
en el ADR que introdujo la migración.

### 6.2 Modelo inicial planificado (baseline 1.0)

Se conserva como referencia de partida; las diferencias con el esquema actual se explican en los ADR indicados.

| Entidad | Campos y constraints clave |
|---|---|
| Branch | id UUID/BIGINT, code UNIQUE, name, address, active, createdAt. |
| AppUser / UserBranch | credenciales/hash, role, active; UNIQUE(user_id,branch_id). |
| Product | id, sku UNIQUE, name, category, active, timestamps. |
| BranchStock | branchId + productId UNIQUE, quantity INTEGER CHECK >= 0, minimumQuantity, version. |
| StockTransfer | id, operationId UUID UNIQUE, sourceBranchId, destinationBranchId, productId, qty, requestFingerprint, actorId, createdAt. |
| StockMovement | id, transferId nullable, branchId, productId, signedQuantity, type, actorId, reason, createdAt. |
| Customer | id, name, phoneNormalized?, email?, consent fields, timestamps. *(v1.3: implementado en V6, ver ADR-011; consentimiento en Fase 4.)* |
| RepairOrder / StatusHistory | id, orderCode UNIQUE, customerId, branchId, assignedTechnicianId?, status, receivedAt, returnedAt?, history with actor. *(v1.3: `returnedAt` = `delivered_at`; más `repair_quotes`, ver ADR-011.)* |
| ServiceRequest / StatusHistory | id, customer/contact snapshot, address, preferredSlot?, status, technicianId?, confirmedAt?, audit timeline. *(v1.5: implementado en V9 como `service_requests` + `service_visits` + `service_request_events`, ver ADR-014.)* |
| AuditEvent | actorId, branchId?, action, entityType, entityId, timestampUtc, correlationId, summary; no secrets. |

Para IDs elegir de manera consistente UUID o BIGINT antes de V2; no mezclar representaciones innecesariamente. No crear tablas futuras durante Fase 0: documentar el modelo y migrar módulo por módulo.

> **Decidido (v1.1):** ver ADR-007 en la sección 17.

## 7. ADR-004: configuración local y Docker

- **ARCH-DEV-001 · MUST** · Docker Desktop Engine debe estar activo: docker --version/compose version comprueban CLI, docker info comprueba conexión al daemon; verificar antes de Testcontainers.
- **ARCH-DEV-002 · MUST** · compose.yaml en raíz con postgres:17, volumen persistente y puerto 127.0.0.1:5433:5432 para evitar colisiones con PostgreSQL local. Healthcheck mediante pg_isready.
- **ARCH-DEV-003 · MUST** · .env con credenciales de Compose ignorado por Git; .env.example sin valores reales. Backend iniciado en PowerShell/VS Code necesita sus propias variables DB_URL, DB_USERNAME, DB_PASSWORD; Docker Compose no inyecta automáticamente su .env al proceso Java del host.
- **ARCH-DEV-004 · MUST** · No imprimir contraseña ni grabarla en launch.json versionado. Documentar script local que lea .env de forma segura o instrucciones de variables de entorno separadas.
- **ARCH-DEV-005 · MUST** · Testcontainers arranca PostgreSQL desechable y ejecuta migraciones; las pruebas unitarias puras no dependen del Engine. No sustituir con H2 si se evalúan constraints/locks de PostgreSQL.

```powershell
# Desde la raiz, cuando Docker Desktop indique "Engine running":
docker info
docker compose up -d db
docker compose ps
# Desde backend (las pruebas usan Testcontainers; spring-boot:run necesita DB_*):
.\mvnw.cmd test
# Desde la raiz: arranca el backend con DB_* derivados de .env
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

## 8. API HTTP y manejo de errores

- **ARCH-API-001 · MUST** · Versionar casos de negocio bajo /api/v1; HTTP apropiado: GET solo lectura, POST acciones nuevas, PATCH modificaciones parciales cuando se utilice. JSON con contratos explícitos.
- **ARCH-API-002 · MUST** · ProblemDetail uniforme para validación, no autorización y conflictos; respuestas sin stack traces ni entidades completas.
- **ARCH-API-003 · MUST** · GET /actuator/health con detalle deshabilitado públicamente; endpoint de demostración /api/v1/ping puede existir temporalmente sin revelar configuración.
- **ARCH-API-004 · MUST** · Paginación con límites de tamaño, orden estable y filtros en repositorio. No devolver listas completas cuando la tabla pueda crecer.

## 9. ADR-005: frontend e integración

React con TypeScript y Vite, organizado por features (`frontend/src/features`: auth, branches, users, inventory, transfers, audit, customers, repairs, service, notifications, dashboard). Un cliente fetch tipado (`shared/api/httpClient.ts`) centraliza credenciales de sesión, cabeceras CSRF y errores `ProblemDetail`. TanStack Query gestiona el estado del servidor (caché e invalidación tras mutaciones); sin Redux.

- **ARCH-FE-001 · MUST** · La UI no es fuente de verdad para stock ni permisos. Revalidar respuestas, invalidar caché tras mutación exitosa y representar explícitamente 401/403/409.
- **ARCH-FE-002 · MUST** · Usar React Router para áreas administrativas; módulo público separado por layout, cargado sin exponer datos internos.

## 10. Testing y CI

| Nivel | Qué verifica | Herramienta |
|---|---|---|
| Unitarias | Reglas puras, permisos de servicio simulados, validaciones | JUnit 5 y Mockito |
| Web y seguridad | HTTP, serialización, 401/403/CSRF, contratos | MockMvc y Spring Security Test |
| Integración | Flyway, constraints, transacciones, locking/replays | Testcontainers PostgreSQL 17 |
| Frontend | Lógica pura, componentes, accesibilidad por teclado, contraste de paletas, typecheck y build | Vitest + Testing Library (jsdom), `scripts/check-contrast.mjs`, `tsc -b`, Vite |
| CI | Backend `verify` + frontend lint/test/contraste/build + higiene del repositorio + migraciones inmutables | GitHub Actions sobre runner con Docker |

CI no depende de una base personal ni de credenciales reales: cada ejecución de pruebas levanta su propio `postgres:17` desechable. Localmente en Windows `.\mvnw.cmd test`; en CI Linux `./mvnw -B verify`. Los informes de Surefire se conservan si algo falla. `.\mvnw.cmd test "-DexcludedGroups=integration"` ejecuta solo las pruebas que no necesitan Docker.

## 11. Logging, auditoría, backups y despliegue

- **ARCH-OPS-001 · MUST** · Registrar errores técnicos con SLF4J/Logback y correlationId, sin PII ni secretos. AuditEvent persistido para acciones críticas de negocio.
- **ARCH-OPS-002 · MUST** · Producción futura: backend y PostgreSQL exclusivos de la aplicación, usuario y credenciales independientes de otros servicios; HTTPS/Nginx, variables protegidas y backups automáticos.
- **ARCH-OPS-003 · MUST** · Antes de operar datos reales, verificar restore de backup, plan de actualización Flyway, límites del disco, retención y controles de acceso.
- **ARCH-OPS-004 · MUST** · No desplegar demo inestable en servidores que alojan otros productos; usar entorno separado o cuotas verificadas para no afectar operaciones existentes.

## 12. ADR-006: decisiones explícitamente descartadas

- No Java 27 con Spring Boot 4.1.1 hasta soporte oficial verificado.
- No JWT como necesidad artificial de la SPA; reevaluar si aparece API móvil/terceros.
- No microservicios, broker, CQRS, repositorio genérico, cache distribuida o Kubernetes en MVP.
- No facturación, pagos, WhatsApp ni almacenamiento de fotografías público hasta definir controles y negocio.
- No compartir PostgreSQL, secretos ni volúmenes con otros proyectos ni hacer cambios manuales al esquema de producción.

## 13. Secuencia de implementación seguida

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Compose PostgreSQL, `.env.example`, configuración Spring, V1, prueba de arranque, health, scaffold React | Hecho |
| 1 | Usuarios, sucursales, bootstrap controlado, login/logout con CSRF, alcance por sucursal (ADR-007, ADR-008) | Hecho |
| 2 | Catálogo, existencias, movimientos, transferencias idempotentes y concurrentes, auditoría (ADR-009) | Hecho |
| 3 | Vista consolidada, auditoría legible, límite de login, clientes, reparaciones, cotizaciones (ADR-010..013) | Hecho |
| 4 | Solicitudes a domicilio, visitas, agenda sin reservas dobles (ADR-014) | Hecho |
| 5 | Repuestos, consentimiento y outbox de avisos, dashboard, sistema de diseño, precios y portal (ADR-015..019) | Hecho |
| — | Identidad pública Electrónica Rojas y estructura del repositorio (ADR-020) | Hecho |
| Piloto | Proveedor de correo, backups/restore, TLS, políticas validadas con el negocio | Pendiente |

## 14. Checklist para publicación de portafolio

- README en inglés con problema, solución, stack, arquitectura, decisiones y trade-offs, sin afirmar adopción
  comercial.
- Repositorio sin secretos ni datos reales; `.env.example` y comandos de instalación que funcionen para un tercero.
- Pruebas reproducibles y CI en verde; capturas con datos ficticios y la identidad vigente.
- Despliegue descrito como demo o piloto solo cuando exista evidencia de esa etapa.

## 15. Referencias técnicas y mantenimiento de ADR

- Spring Boot 4.1.1 requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring Security CSRF para SPA: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- Spring Boot testcontainers: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Flyway PostgreSQL: https://documentation.red-gate.com/fd/postgresql-database-277579324.html
- Docker Compose: https://docs.docker.com/compose/

Cualquier cambio de Java/Boot, esquema de transacciones, modelo de seguridad, DB, infraestructura o estrategia de pruebas requiere un nuevo ADR o revisión versionada con fecha, problema, alternativas, decisión y consecuencias.

## 16. Runbook

El diagnóstico local (Docker, variables `DB_*`, Flyway, puertos, Testcontainers, límites de login y del
formulario, avisos) está en [`runbook.md`](runbook.md).

- Secuencia de soporte: reproducir, conservar el log con secretos redactados, localizar la causa raíz, hacer el
  cambio mínimo, ejecutar la prueba de regresión y documentar la solución.
- Un respaldo operativo exige restauración verificada en una base separada; un volumen Docker local no es un
  respaldo.

## 17. ADR-007: identificadores internos BIGINT y referencias públicas UUID

- **Estado:** ADOPTADO · 25 de septiembre de 2026 · aplica desde V2.
- **Contexto:** la sección 6 exige elegir UUID o BIGINT antes de V2 sin mezclar representaciones innecesariamente.
- **Decisión:** toda tabla usa clave primaria `id BIGINT GENERATED ALWAYS AS IDENTITY` (Java `Long`, `@GeneratedValue(strategy = IDENTITY)`); las claves foráneas son BIGINT. Se añade una columna `UUID NOT NULL UNIQUE` independiente solo cuando un identificador debe salir del contexto interno autenticado o generarse en el cliente: `operationId` de transferencias (ARCH-DB-003), enlaces públicos de consulta (FR-NOT-002) y referencias de solicitudes públicas (FR-SRV-002).
- **Alternativas descartadas:** UUID como clave primaria en todas las tablas (índices y FKs de 16 bytes, peor localidad de inserción con UUID aleatorios, menor legibilidad en soporte); BIGINT expuesto también en superficies públicas (secuencial y enumerable).
- **Consecuencias:** la API interna `/api/v1` puede exponer IDs BIGINT a colaboradores autenticados porque la protección proviene de la autorización por rol y sucursal (ARCH-SEC-005), nunca del secreto del identificador; las pruebas IDOR son obligatorias. `GENERATED ALWAYS` impide insertar IDs manuales por error. La estrategia IDENTITY desactiva inserciones JDBC por lotes en Hibernate, aceptable para los volúmenes previstos. V1 no se modifica.

## 18. ADR-008: implementación de sesión, CSRF y revocación (Fase 1)

- **Estado:** ADOPTADO · 25 de septiembre de 2026. Concreta ADR-002 sin cambiar su modelo.
- **Autenticación:** filtro estándar de form login de Spring Security en `POST /api/v1/auth/login` con manejadores JSON (204/401 ProblemDetail); `DaoAuthenticationProvider` con `UserDetailsService` propio y `DelegatingPasswordEncoder` (BCrypt, prefijo `{bcrypt}`). Se conserva la protección estándar contra fijación de sesión (rotación del id) y la renovación del token CSRF al autenticarse. Sin `RequestCache`, para no crear sesiones en cada 401 anónimo.
- **CSRF SPA:** `csrf.spa()` de Spring Security 7 con `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` no HttpOnly, SameSite=Lax, Secure con `SECURE_COOKIES=true`) y cabecera `X-XSRF-TOKEN`. Cookie de sesión HttpOnly, SameSite=Lax, 30 min, sin id en la URL.
- **Revocación:** columna `app_users.security_version` incrementada al cambiar rol, estado o contraseña; un filtro tras `SecurityContextHolderFilter` recarga la cuenta en cada petición autenticada e invalida la sesión si difiere (equivalente al security stamp de ASP.NET Core Identity).
- **Alcance por sucursal:** `BranchService` es el único punto de decisión; lee `user_branches` en cada llamada mediante SQL nativo para que el módulo `branches` no dependa de la entidad del módulo `users` (dependencias: users → branches → security, sin ciclos). Roles con `@PreAuthorize` en la capa de aplicación.
- **Último administrador:** verificación con `SELECT ... FOR UPDATE` sobre los administradores activos para serializar degradaciones concurrentes.
- **Pruebas:** las pruebas de integración envían CSRF como el navegador (cookie + cabecera) en lugar de `csrf()` de Spring Security Test, que sustituye de forma permanente el repositorio de tokens del contexto compartido; el flujo SPA completo se verifica además sobre Tomcat real.
- **Consecuencias:** una consulta por petición autenticada para revalidar; sesiones en memoria (ARCH-SEC-006). Pendiente antes del piloto: limitación de intentos de login (SEC-006) — resuelto en v1.3, ver ADR-010.

## 19. ADR-009: consistencia de inventario, idempotencia y auditoría (Fase 2)

- **Estado:** ADOPTADO · 26 de septiembre de 2026. Concreta ADR-003 (ARCH-DB-001..005) y OBS-001/002.
- **Punto único de cambio de existencias:** `StockLedger` crea las filas que faltan con `INSERT ... ON CONFLICT (branch_id, product_id) DO NOTHING` (ARCH-DB-004) y bloquea las filas implicadas con `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) en orden ascendente de `branch_id` para un mismo producto, que es el orden global (branch_id, product_id) de ARCH-DB-005. Movimientos, transferencias y cambios de mínimo pasan por él; `@Version` en `branch_stock` queda como segunda defensa.
- **Aislamiento:** READ COMMITTED (valor por defecto de PostgreSQL). El bloqueo de fila, y no el nivel de aislamiento, garantiza que el saldo leído siga vigente al escribir; `SELECT ... FOR UPDATE` relee la última versión confirmada tras esperar.
- **Transferencia:** una transacción `@Transactional` descuenta origen, suma destino, inserta cabecera, dos movimientos y dos eventos de auditoría (uno por sucursal); cualquier excepción revierte todo.
- **Idempotencia:** el cliente envía `operationId`; se guarda con una huella SHA-256 de los datos de negocio. Se comprueba antes de bloquear (repetición rápida) y otra vez después de obtener el bloqueo (duplicado concurrente confirmado mientras se esperaba). Mismo id con otros datos u otro actor es 409. Refuerzo en base de datos: `UNIQUE (operation_id)` en `stock_transfers` y `UNIQUE (operation_id, branch_id)` en `stock_movements`.
- **Modelo (amplía la sección 6):** `products` (sku, name, category, description, kind, unit=UNIT, active, version); `branch_stock` (UNIQUE branch/product, CHECK quantity ≥ 0, minimum_quantity, version); `stock_transfers` según la sección 6; `stock_movements` añade `operation_id`, `balance_after`, `request_fingerprint` y CHECK de signo por tipo, motivo obligatorio fuera de transferencias y vínculo obligatorio con la transferencia en sus dos tramos.
- **Historial inmutable:** entidades `@Immutable` y trigger `reject_history_change()` que rechaza UPDATE/DELETE en `stock_movements`, `stock_transfers` y `audit_events` (BR-TRF-005, DATA-005). Las pruebas vacían esas tablas con TRUNCATE.
- **Auditoría:** tabla `audit_events` (V4) con actor (id + nombre instantáneo, sin FK de código hacia `users`), acción, entidad, sucursal, `operationId`, `correlation_id` y resumen saneado. `AuditService.record` usa `Propagation.MANDATORY` para escribir solo dentro de la transacción del caso de uso. Retroactivamente se auditan también sucursales, usuarios, restablecimiento de contraseña y bootstrap.
- **Correlación:** `CorrelationIdFilter` asigna `X-Request-Id` (acepta solo `[A-Za-z0-9-]{8,64}` del cliente), lo pone en el MDC y en cada línea de log mediante `logging.pattern.correlation`.
- **Errores:** deadlock o bloqueo no obtenido (`PessimisticLockingFailureException`) → 409 reintentable con el mismo `operationId`.
- **Evidencia:** pruebas concurrentes reales sobre HTTP (8 sesiones simultáneas). Sin el bloqueo pesimista solo 1 de 8 retiradas prospera sobre 3 unidades (`@Version` evita el saldo negativo, pero rechaza de más); bloqueando en el orden de la petición en vez de por `branch_id`, PostgreSQL registra `deadlock detected`.
- **Consecuencias:** transacciones cortas y serializadas por (sucursal, producto); sin tiempo máximo de espera de bloqueo configurado todavía (evaluar `lock_timeout` antes del piloto). Transferencias con tránsito (BR-TRF-006) siguen fuera del MVP.

## 20. ADR-010: auditoría legible, vista consolidada y protección del inicio de sesión (Fase 3, etapa A)

- **Estado:** ADOPTADO · 26 de septiembre de 2026.
- **Detalles de auditoría:** V5 añade `audit_events.details JSONB` (mapeado con `@JdbcTypeCode(SqlTypes.JSON)` a `Map<String,Object>`) e índice `(action, occurred_at DESC)`. Cada caso de uso registra datos estructurados neutrales al idioma (códigos, cantidades, nombres instantáneos de sucursal/producto), nunca datos personales de clientes ni secretos. `AuditEntry` es un builder que omite nulos y convierte enums a su nombre.
- **Historial anterior:** los eventos previos a V5 no se reescriben. `LegacyAuditSummaries` reconoce los formatos de resumen de la Fase 2 y los expone como `detailsSource=LEGACY`; si no se reconocen, `NONE` y la SPA muestra un texto genérico más el texto técnico original bajo demanda.
- **Idioma:** el backend mantiene códigos y mensajes técnicos en inglés (son contrato e historial); toda traducción vive en `frontend/src/shared/i18n` (etiquetas, descripciones de auditoría y códigos de error estables `errors[].code` / `code` de 409). Las fechas se filtran por días calendario de America/Costa_Rica (`ClockConfig.BUSINESS_ZONE`) y se guardan en UTC.
- **Vista consolidada:** `GET /api/v1/stock/overview` pagina productos en SQL con el alcance del usuario aplicado en la consulta (lista de sucursales autorizadas) y trae las celdas de la página en una segunda consulta `IN (...)`: dos consultas por página, sin N+1. `StockStatus.of(cantidad, mínimo)` es la única definición de los estados de existencias.
- **Bloqueo de inicio de sesión:** `LoginAttemptLimiter` en memoria (monolito de una instancia, sin Redis — ADR-006): 5 fallos por cuenta o 20 por IP en una ventana de 15 minutos bloquean 15 minutos; `LoginThrottleFilter` responde 429 con `Retry-After` antes de autenticar. Se cuenta por correo aunque la cuenta no exista, para no revelar su existencia. Configurable con `app.security.login.*` (prefijo renombrado en ADR-020).
- **Consecuencias:** el contador se pierde al reiniciar y no se comparte entre instancias; si se escala horizontalmente, mover el estado a PostgreSQL o a un proxy. Detrás de un proxy inverso, la IP del cliente debe venir de una cabecera de confianza configurada explícitamente.

## 21. ADR-011: clientes, órdenes de reparación y flujo de estados (Fase 3, etapa B)

- **Estado:** ADOPTADO · 26 de septiembre de 2026. Implementa ER-BR-001 v1.2 (BR-CUS-001..004, BR-REP-001..010).
- **Módulos:** `customers` (entidad, normalización de teléfono `PhoneNumbers` a E.164, servicio y controlador) y `repairs` (`RepairOrder`, `RepairStatusChange`, `RepairQuote`, `RepairPolicy`, `RepairWorkflow`, `RepairOrderAccess`, servicios y controlador). `repairs` depende de `customers` y `users` solo por servicios públicos (`CustomerService.register/requireForIntake`, `TechnicianDirectory`); los repositorios quedan privados a su módulo.
- **Modelo (V6):** `customers` (teléfono E.164 con CHECK, índice no único; sucursal de registro; `version`); `repair_orders` (código `OR-AAAA-NNNNNN` UNIQUE a partir de una secuencia, `intake_operation_id` UUID UNIQUE + huella SHA-256, datos del equipo inmutables, `status` y `resolution` con CHECK, técnico, diagnóstico, recepción y entrega; CHECK de coherencia `status='DELIVERED'` ⇔ `delivered_at` no nulo; el trigger que impide DELETE llega en V7, ver ADR-012); `repair_status_history` (append-only con `reject_history_change()`, CHECK `from ≠ to`); `repair_quotes` (`NUMERIC(12,2)` > 0, `CHAR(3)='CRC'`, índice único parcial "una pendiente por orden", trigger que rechaza modificar o borrar una cotización decidida). Índices por sucursal/fecha, cliente, técnico+estado y serie.
- **Máquina de estados:** `RepairPolicy` es una clase pura (sin Spring ni base de datos, probada de forma exhaustiva) con la tabla de transiciones, las transiciones que solo produce una cotización, los permisos por rol/técnico asignado y los requisitos de motivo y técnico. El servidor la aplica en `RepairWorkflow`, único código que cambia el estado, y la API devuelve sus respuestas como `actions` para que la SPA solo ofrezca lo permitido.
- **Concurrencia:** todo cambio de una orden (estado, técnico, diagnóstico, cotización) bloquea su fila con `SELECT ... FOR UPDATE`; dos entregas simultáneas se serializan y la segunda ve DELIVERED y recibe 409. La recepción idempotente toma un `pg_advisory_xact_lock` derivado del `operationId`: los duplicados simultáneos esperan al primero y devuelven la misma orden en lugar de competir por el cliente y la orden. `@Version` protege además el diagnóstico y la edición de clientes.
- **Transacciones:** cada caso de uso es una transacción de la capa de aplicación; la auditoría (`MANDATORY`) y el historial se escriben en ella. Una falla a mitad de la recepción no deja cliente, orden, historial ni auditoría (probado con un trigger de prueba).
- **Acceso y privacidad:** `RepairOrderAccess` decide la visibilidad (404 indistinguible fuera del alcance; el técnico solo ve sus órdenes). El ámbito de clientes se aplica en SQL. Técnicos no reciben teléfono ni correo. Auditoría y logs sin datos personales; el motivo de un cambio queda en el historial de la orden, no en la auditoría.
- **Punto de integración preparado (implementado en v1.6, ADR-015):** consumo de repuestos (BR-REP-007) como caso de uso en `repairs` que llame al `StockLedger` de inventario con un movimiento `OUT_FOR_REPAIR` vinculado a la orden, dentro de la misma transacción y bajo el bloqueo de la orden. Ninguna operación de Fase 3 toca existencias.
- **Consecuencias y Fase 4:** referencia pública con UUID para el seguimiento del cliente (ADR-007, FR-NOT-002); consentimiento por canal y outbox de notificaciones disparadas tras confirmar la transacción (BR-NOT-002), p. ej. al llegar a READY_FOR_PICKUP; agenda de técnicos a partir de `assigned_technician_id` y del futuro `ServiceRequest`; las solicitudes a domicilio reutilizarán `customers` y `PhoneNumbers`.

## 22. ADR-012: las migraciones aplicadas son inmutables, también en desarrollo (incidente V6)

- **Estado:** ADOPTADO · 26 de septiembre de 2026. Refuerza ENG-034 y ARCH-DB-006.
- **Incidente:** V6 se escribió a las 12:06:37 (hora de Costa Rica). Con el backend de desarrollo en marcha, `spring-boot-devtools` reinició la aplicación al detectar el recurso nuevo y Flyway aplicó V6 a las 12:06:42. A las 12:06:45 se añadió al final del archivo el trigger `trg_repair_orders_no_delete`. En el siguiente arranque, Flyway rechazó el historial: *checksum mismatch* en la versión 6 (registrado 488491709, archivo −1030986746). El archivo no estaba versionado en Git.
- **Recuperación sin reescribir historia:** respaldo `pg_dump -Fc` verificado con `pg_restore --list`, fuera del repositorio. El contenido original de V6 se reconstruyó desde el registro de la sesión que lo escribió y se validó reproduciendo el algoritmo de Flyway (CRC32 por línea): coincide con 488491709. El esquema real se comparó objeto por objeto (`pg_dump --schema-only`) con el de V1–V5 más el V6 original, aplicados en un PostgreSQL 17 desechable: son idénticos. Se restauró V6 exactamente y el trigger pasó a `V7__repair_orders_no_delete.sql`. No se usó `flyway repair`, no se editaron checksums ni se borraron tablas o datos.
- **Reglas:**
  - Una migración que ya se aplicó en cualquier base (incluida la local) no se edita. Todo cambio va en `V{n+1}`, aunque sea de un minuto después.
  - Una migración se escribe completa y se revisa antes de arrancar o reiniciar el backend. `spring.devtools.restart.additional-exclude=db/migration/**` evita que guardar el archivo reinicie la aplicación; se aplica en el siguiente reinicio explícito.
  - La migración se versiona en Git (commit local) en cuanto se aplica por primera vez, para que Git sea la fuente de recuperación.
  - `flyway repair` o el cambio de checksum solo con autorización explícita, y solo tras demostrar que el esquema real coincide con el archivo.
  - Si el cambio aún no se aplicó en ninguna base compartida y los datos locales son desechables, la alternativa es recrear la base local; nunca con datos que se quieran conservar.
- **Consecuencias:** puede haber migraciones pequeñas y seguidas (V6 + V7) en lugar de una sola "perfecta"; es el precio de un historial reproducible idéntico en desarrollo, CI y producción.

## 23. ADR-013: búsqueda incremental de clientes (Fase 3.1)

- **Estado:** ADOPTADO · 27 de septiembre de 2026.
- **Decisión:** columna `customers.search_name` (V8) con el nombre en minúsculas, sin diacríticos y con espacios simples, calculada por `SearchText.normalize` en cada escritura (el *backfill* de V8 usa `translate()` para las filas previas). La búsqueda exige que **cada palabra** aparezca (`LIKE '%palabra%'`, hasta 3 palabras) o que el teléfono contenga los dígitos escritos. Índices GIN `pg_trgm` sobre `search_name` y `phone`, porque un B-tree no sirve para "contiene"; `pg_trgm` viene con PostgreSQL y es una extensión confiable. Resultados paginados (máx. 10) y alcance aplicado en SQL.
- **Frontend:** una TanStack Query por término (debounce de 300 ms); la clave de consulta garantiza que una respuesta vieja no reemplace a la nueva y el `AbortSignal` cancela la petición anterior.
- **Alternativas descartadas:** `unaccent` en la consulta (función no inmutable, no indexable directamente); búsqueda de texto completo (excesiva para nombres); descargar clientes al navegador (fuga de datos personales).
- **Consecuencias:** una escritura por cliente mantiene `search_name`; con decenas de miles de clientes la búsqueda sigue en milisegundos. Si se escribe fuera de la aplicación, hay que recalcular `search_name`.

## 24. ADR-014: solicitudes a domicilio, agenda y prevención de reservas dobles (Fase 4)

- **Estado:** ADOPTADO · 27 de septiembre de 2026. Implementa ER-BR-001 v1.3 (BR-SRV-001..008).
- **Referencias funcionales consultadas (documentación pública):** Jobber separa *requests* (datos y fechas preferidas del cliente, revisión antes de programar) de *assessments/visits* asignadas a personal, con estados hasta *Converted*, y solo **avisa** de conflictos de agenda; RepairDesk convierte *leads/appointments* en tickets asignables a técnicos con vistas de calendario. **Decisiones propias de este proyecto:** el conflicto se **impide** (no solo se avisa), una solicitud nunca se convierte sola en cita y el cliente nunca se asocia automáticamente.
- **Modelo (V9):** `service_requests` (canal PUBLIC_FORM o STAFF, instantánea de contacto y dirección, `customer_id` nulo hasta la resolución de identidad, `public_ref` UUID para el cliente, `submission_id` para idempotencia); `service_visits` (única fuente de la hora: `scheduled_start`, `scheduled_end`, `blocked_until` = fin + margen; índice único parcial "una visita activa por solicitud"); `technician_shifts` (una jornada por técnico y día, con descanso); `branch_service_settings`; `service_request_events` (append-only). Borrado impedido por triggers en solicitudes y visitas.
- **Estados:** `ServicePolicy`, clase pura con dos tablas (solicitud y visita) y las reglas por rol; `ScheduleRules`, aritmética pura de jornada, descanso, margen y superposición en hora de Costa Rica. Ambas con pruebas unitarias exhaustivas.
- **Prevención de reservas dobles (dos capas):**
  1. Al validar y escribir tiempo confirmado, la transacción toma `pg_advisory_xact_lock(2, technician_id)`: las confirmaciones del mismo técnico se serializan y la segunda ve la primera y responde 409 `SCHEDULE_CONFLICT` con la visita en conflicto. La solicitud y la visita se bloquean con `SELECT … FOR UPDATE`, en orden fijo (primero la solicitud, después la visita).
  2. Restricción `EXCLUDE USING gist (technician_id WITH =, tstzrange(scheduled_start, blocked_until) WITH &&) WHERE status IN ('CONFIRMED','IN_PROGRESS')` (extensión `btree_gist`): aunque falle el código, PostgreSQL no guarda dos visitas confirmadas superpuestas (SQLState 23P01, traducido a `SCHEDULE_CONFLICT`). `blocked_until` se almacena porque `timestamptz + interval` no es inmutable y no puede usarse en un índice.
  Evidencia: 6 confirmaciones HTTP simultáneas de la misma franja producen exactamente 1 éxito y 5 conflictos; con franjas parcialmente superpuestas, ningún par confirmado se solapa.
- **Formulario público:** `/api/v1/public/**` es lo único anónimo. CSRF sigue activo (la SPA obtiene el token con `/api/v1/auth/csrf`). Hay límite en memoria por IP y por teléfono (`PublicRequestLimiter`, como el de inicio de sesión; sin Redis), un campo trampa, idempotencia con un bloqueo consultivo sobre `submissionId` y una consulta de estado solo por UUID. No existe ningún endpoint anónimo que busque clientes.
- **Integración:** `VisitService.linkRepairOrder` reutiliza `RepairOrderService.receive` (misma transacción, mismo cliente, idempotente). El módulo `servicerequests` depende de `repairs`, `customers` y `users` solo mediante sus servicios públicos; `repairs` no conoce a `servicerequests` (la SPA pide la visita de origen por `/service-visits/by-repair-order`).
- **Hallazgos técnicos:** con `hibernate.jdbc.time_zone=UTC`, las columnas `TIME` se desplazaban según la zona de la JVM; `TechnicianShift` usa `@JdbcType(LocalTimeJdbcType.class)`. Una excepción de negocio lanzada dentro de un método `@Transactional` y capturada por el llamador marca la transacción como *rollback-only*; las comprobaciones de visibilidad usan `BranchService.isReadable/isOperable`, que no lanzan excepciones.
- **Pendiente / consecuencias:** el limitador en memoria no se comparte entre instancias y, detrás de un proxy, necesita la IP real (cabecera de confianza). No hay cálculo automático de traslados (margen fijo por sucursal). El consumo de repuestos durante la reparación (BR-REP-007) sigue preparado como caso de uso de `repairs` sobre `StockLedger`, sin implementar.

## 25. ADR-015: consumo de repuestos en reparaciones sobre el `StockLedger` (Fase 5)

- **Estado:** ADOPTADO · 27 de septiembre de 2026. Implementa BR-REP-007 y BR-REP-011..013 (ER-BR-001 v1.4); concreta el punto de integración previsto en ADR-011.
- **Frontera entre módulos:** `repairs` no toca `branch_stock`. Llama a `inventory.RepairStock`, única API pública con la que otro módulo cambia existencias; esta bloquea la fila con `StockLedger` (el mismo de movimientos y transferencias), aplica el saldo y escribe un `StockMovement` `OUT_FOR_REPAIR` / `RETURN_FROM_REPAIR` con `repair_order_id`. `inventory` no depende de `repairs`: guarda el id como columna simple (FK en la base) y lee el código de la orden con una `@Formula` de solo lectura. Los escritos usan `Propagation.MANDATORY`: siempre dentro de la transacción del caso de uso de `repairs`.
- **Modelo (V10):** `stock_movements` admite los dos tipos nuevos, con CHECK de signo, `repair_order_id` obligatorio exactamente para ellos y motivo obligatorio en la devolución. `repair_part_usages` (orden, producto, sucursal, cantidad 1..1000, `returned_quantity` con CHECK `0 ≤ devuelto ≤ consumido`, `operation_id` UNIQUE, huella, `movement_id` UNIQUE) con un disparador que solo permite aumentar `returned_quantity` y rechaza DELETE; `repair_part_returns` append-only con motivo y estado de la orden al corregir.
- **Orden de bloqueo:** orden (`SELECT … FOR UPDATE`) → línea de repuesto (para devoluciones) → fila de existencias (por `StockLedger`, en orden (sucursal, producto)). Los movimientos y transferencias bloquean solo existencias, y los cambios de estado solo la orden: ninguna operación adquiere estos recursos en sentido inverso, así que no puede formarse un ciclo de espera. Prueba: consumos y transferencias opuestas del mismo producto en paralelo terminan todas con 201 y sin `deadlock detected` en el log.
- **Idempotencia:** `operationId` por intención, con huella SHA-256 (orden, producto, cantidad, nota) y autor. La comprobación de repetición ocurre **después** de obtener el bloqueo de la orden: los duplicados simultáneos esperan al primero y devuelven su resultado (200); UNIQUE en `repair_part_usages/returns.operation_id` y en `stock_movements (operation_id, branch_id)` lo respaldan. Un id ya usado en un movimiento o transferencia de inventario es 409.
- **Atomicidad:** línea, movimiento, saldo y auditoría en una transacción; pruebas con disparadores que fallan a mitad (auditoría o línea) dejan saldo, movimientos y líneas intactos.
- **Evidencia:** 6 técnicos compitiendo por 2 unidades → 2 × 201 y 4 × 409 `INSUFFICIENT_STOCK`, saldo 0; 6 duplicados simultáneos → 1 × 201 y 5 × 200; 6 devoluciones paralelas de 1 unidad sobre 3 consumidas → 3 aceptadas y 3 × 409.
- **Consecuencias:** el técnico ve existencias solo a través de su orden (`/repair-orders/{id}/parts/options`), sin permisos de inventario. No hay costo ni precio del repuesto (sin facturación).

## 26. ADR-016: consentimiento por canal y *transactional outbox* de avisos (Fase 5)

- **Estado:** ADOPTADO · 27 de septiembre de 2026. Implementa BR-CUS-006, BR-NOT-002 y BR-NOT-004..006. Sustituye la nota de BR-NOT-002 «introducir outbox solo cuando se implemente el canal real»: se implementa ya, con un transporte de desarrollo, para que el flujo completo sea demostrable sin enviar correos reales.
- **Problema:** enviar correo dentro de la transacción de negocio acopla la operación al proveedor (una caída del SMTP revertiría la entrega del equipo, BR-REP-006) y puede anunciar algo que luego se revierte. Enviar después del commit en el mismo hilo pierde el aviso si el proceso muere entre ambos pasos.
- **Decisión:**
  1. **Consentimiento** (`customers`): tabla append-only `customer_consents`; vigente = última declaración por (cliente, canal). `CustomerConsentService` es la única fuente de verdad y publica `ConsentChanged` (evento de Spring síncrono, misma transacción) para que `notifications` detenga lo pendiente sin que `customers` dependa de `notifications`.
  2. **Outbox** (`notification_outbox`, V11): `RepairWorkflow` (al pasar a `READY_FOR_PICKUP`) y `ServiceTimeline` (visita confirmada / reprogramada / cancelada) llaman a `NotificationOutbox.enqueue` con `MANDATORY`: el aviso se guarda con la operación o no se guarda. Clave `dedupe_key` UNIQUE = evento : id del evento de origen (fila del historial) : canal : cliente. Sin consentimiento o sin dirección se guarda como `SKIPPED` para que el personal lo sepa.
  3. **Trabajador** (`NotificationDispatcher`, `@Scheduled` con `fixedDelay`): reclama en una transacción corta con `UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED LIMIT n) RETURNING id`, que asigna cada fila a un único trabajador sin esperas entre ellos, y marca `SENDING` con un *lease* (`locked_by`, `locked_until`). Fuera de cualquier transacción vuelve a comprobar consentimiento y dirección, compone y envía; después registra el resultado en otra transacción corta, **cercado** por `status = 'SENDING' AND locked_by = :worker`, y agrega una fila a `notification_attempts`. Un `SENDING` con *lease* vencido (trabajador caído) vuelve a reclamarse.
  4. **Reintentos:** errores temporales esperan 1, 5, 15 y 60 min hasta `max_attempts` (5); errores permanentes fallan en el acto; ADMIN/BRANCH_MANAGER pueden reintentar un `FAILED` (auditado). Nunca se reenvía un `SENT`.
  5. **Composición y transporte separados:** `NotificationComposer` (puro, plantillas en español, sin HTML) y la interfaz `MailTransport` con dos implementaciones: `InboxMailTransport` (predeterminada; memoria, últimos 100 mensajes, visible solo para ADMIN, puede simular fallos) y `SmtpMailTransport` (`spring-boot-starter-mail`), activa solo con `MAIL_MODE=smtp` y `spring.mail.*` desde variables de entorno o secretos externos; si falta el servidor, el arranque falla en lugar de simular envíos. El *health* de correo se desactiva para que una caída del SMTP no marque la aplicación como caída.
- **Garantías (sin prometer lo imposible):** nunca antes del commit; nunca con consentimiento retirado antes de reclamar el mensaje (la retirada además marca los pendientes como `SKIPPED` en su misma transacción); al menos una vez hacia el proveedor. SMTP no ofrece claves de idempotencia: si el proceso muere después de que el servidor aceptó el mensaje y antes de registrar `SENT`, el mensaje se reenviará al vencer el *lease*; el `Message-ID` estable (`<outbox-{id}@electronica-rojas.notifications>`) permite a los clientes de correo reconocer el duplicado. Una retirada que se confirma durante la llamada SMTP ya en curso no puede detener ese mensaje.
- **Privacidad:** el *payload* guarda solo parámetros de plantilla (códigos, equipo, sucursal, fechas); el nombre y el correo se leen al enviar. Los logs y la administración muestran estado, códigos y la dirección enmascarada (`l***@dominio`), nunca el contenido.
- **Alternativas descartadas:** Kafka/RabbitMQ/Redis (ADR-006; PostgreSQL basta para este volumen), `@TransactionalEventListener(AFTER_COMMIT)` con envío directo (se pierde el aviso si el proceso muere y no hay reintentos durables), enviar dentro de la transacción.
- **Evidencia:** pruebas con PostgreSQL real: fila en la misma transacción y ninguna si la transacción falla; retirada antes del envío y revalidación del trabajador; 5 intentos → `FAILED` → reintento manual → `SENT`; rechazo permanente; *lease* vencido; 4 trabajadores concurrentes sobre 30 mensajes → 30 envíos, cero duplicados.
- **Pendiente:** proveedor SMTP autorizado, SPF/DKIM del dominio, WhatsApp (plantillas aprobadas), retención/purga de filas antiguas y métricas. Con varias instancias el diseño ya funciona (SKIP LOCKED + *lease*).

## 27. ADR-017: dashboard operativo consistente con los listados (Fase 5)

- **Estado:** ADOPTADO · 27 de septiembre de 2026. Implementa FR-DSH-001..003.
- **Decisión:** módulo `dashboard` de solo lectura que compone los casos de uso públicos existentes (`RepairOrderService.list`, `ServiceRequestService.list`, `VisitService.agenda`, `BranchStockService.list/overview`) pidiendo **una página de un elemento** y leyendo `totalElements`. Cada número es, por construcción, el `COUNT(*)` del listado que abre su tarjeta, con el mismo filtro, el mismo alcance por sucursal (en SQL) y la misma autorización (`@PreAuthorize`, 404 fuera de alcance). El navegador no descarga registros para contarlos.
- **Alcance:** `branchId` presente = una sucursal (validada con `BranchService.requireReadable`); ausente = todas las del usuario (ADMIN y BRANCH_MANAGER; recepción debe elegir una; el técnico ve su trabajo). Enlaces de la SPA: listados con `status` y `branch` en la URL, agenda con `branch=all` en la vista consolidada, vista consolidada de existencias.
- **Costo:** unas 8–10 consultas de conteo por carga (cada una indexada) y un día y 7 días de agenda acotados; se refresca cada 60 s. Aceptable para el volumen de una electrónica; si creciera, se pasaría a consultas de conteo agregadas en el mismo repositorio de cada listado, conservando la misma cláusula WHERE.
- **Navegación:** la barra muestra los módulos diarios (Reparaciones, A domicilio, Agenda o Mis visitas, Clientes, Inventario) y agrupa el resto en un menú *Más* con patrón de divulgación accesible (ARIA, Escape, clic fuera), sin librería de componentes nueva.

## 28. ADR-018: sistema de diseño con variables CSS y división de código por rutas

- **Estado:** ADOPTADO · 27 de septiembre de 2026. Detalle en `docs/design-system.md` (ER-DS-001).
- **Contexto:** la SPA creció con estilos por pantalla (una sola hoja de 1 300 líneas, un radio y una sombra),
  sin navegación móvil y con un único paquete JavaScript de 564 kB.
- **Decisión:** tokens en variables CSS (`src/styles/tokens.css`) y capas de estilo (base, componentes, shell,
  módulos); paletas como `data-theme` en `<html>`, elegidas por el usuario y guardadas en `localStorage`
  (preferencia visual, sin backend). Componentes compartidos propios (`PageHeader`, `SectionCard`, estados,
  `FilterBar`, `Icon` con SVG propios). `React.lazy` por ruta en `app/router.tsx`; el inicio y el shell siguen
  en el paquete principal.
- **Alternativas descartadas:** Tailwind, Bootstrap o Material UI (dependencia y reescritura del marcado solo
  por estilo; el proyecto ya tenía clases semánticas), librería de iconos (peso y licencia para ~50 iconos),
  fuentes web (petición externa y bloqueo del render), preferencia de paleta en el servidor (migración y
  endpoint sin valor de negocio).
- **Verificación:** `npm run check:contrast` comprueba los contrastes de cada paleta (también en CI); pruebas
  de teclado del menú lateral, la barra inferior y el menú de cuenta; barrido de 26 pantallas en 9 anchos
  (320–1440 px) sin desplazamiento horizontal.
- **Consecuencias:** el JavaScript inicial baja a 274 kB (84 kB gzip); una pantalla nueva debe usar tokens y
  componentes existentes; cambiar la identidad es cambiar tokens, no pantallas.

## 29. ADR-019: montos como instantánea, existencias iniciales por el `StockLedger` y portal público de un solo registro

- **Estado:** ADOPTADO · 28 de septiembre de 2026. Implementa BR-INV-007/008, BR-REP-014 y BR-SRV-009 (ER-BR-001 v1.6); contrato en ER-FS-001 §25.
- **Dinero:** `NUMERIC(12,2)` en PostgreSQL y `BigDecimal` con escala 2 en Java, como `repair_quotes.amount`; nunca `float`/`double`. Nulo = desconocido; CHECK `>= 0` en la base además de `@PositiveOrZero @Digits(10,2)` en la API. Comparaciones con `compareTo` (15000 y 15000.00 son el mismo monto). Moneda única CRC, sin conversión.
- **Costo solo para gestión:** `PricingPolicy.mayViewCost` (ADMIN, BRANCH_MANAGER) es la única regla; los DTO de producto y de líneas ponen `unitCost` en nulo para los demás roles (Jackson lo omite), y las opciones de repuestos del técnico no lo incluyen nunca. La SPA solo refleja lo que recibe.
- **Instantánea de precio (V12):** `repair_part_usages` añade `unit_price`, `unit_cost`, `chargeable` y `price_overridden`, escritos una sola vez al consumir. El disparador `guard_repair_part_usage()` (V10) se reemplaza para rechazar también cualquier cambio de esas columnas: la base garantiza que un precio histórico no se reescribe. Los totales (`PartCharges`, clase pura probada en unidad) se calculan al leer con las unidades todavía en uso, así una devolución baja el subtotal sin tocar la línea. Las líneas anteriores a V12 quedan con precio nulo y cobrables («precio por definir»): se prefirió no inventar datos.
- **Cotización y subtotal separados:** `repair_quotes.amount` es un total aprobado sin desglose; sumarle repuestos podría cobrar dos veces. Se exponen como métricas separadas. Evolución prevista: líneas de cotización/factura (mano de obra, repuestos, otros) que referencien las líneas de repuesto; solo entonces un total compuesto.
- **Quién decide el cobro:** técnico registra con los valores del catálogo; otro precio o «sin cargo» exige ADMIN/BRANCH_MANAGER (`RepairPolicy.mayOverridePartPricing`) y se valida **antes** de tocar existencias (`RepairStock.requireSparePart` separa la lectura del producto del consumo). La huella de idempotencia solo incluye precio/cobro cuando se envían, para que un reintento anterior a V12 siga coincidiendo.
- **Existencias iniciales:** `ProductCatalogService.create` crea el producto y, en la misma transacción, bloquea la fila con `StockLedger`, aplica una entrada `RECEIPT` y audita; una falla (probada con un disparador) revierte también el producto. Se reutiliza `RECEIPT` en lugar de un tipo nuevo: evita tocar las restricciones CHECK de `stock_movements` y los filtros; el motivo fijo y `details.initialStock` en la auditoría la distinguen. El `operationId` lo genera el servidor porque el SKU único ya convierte un doble envío en 409.
- **Portal (V12):** `public_portal_settings` con una sola fila (`CHECK id = 1`, sembrada por la migración con el comportamiento previo) y `version` para concurrencia; listas (días, provincias, tipos) en JSONB como `audit_events.details`. Solo se guarda el slug; la URL la arma la SPA con `window.location.origin` (sin dominio fijo). `public_portal_slug_history` (append-only) conserva los slugs anteriores: `GET /public/portal/{slug}` acepta el actual o uno anterior y responde con el actual, así un QR impreso sigue funcionando. Pausar no devuelve 404: la página explica la pausa y `POST /public/service-requests` responde 409 `PORTAL_DISABLED` **después** de comprobar si es un reintento (la idempotencia se respeta). Las reglas se validan en `PortalRules` (pura) y en el servidor; la fecha mínima/máxima se calcula en hora de Costa Rica y se envía ya resuelta.
- **Superficie anónima:** solo se agregan dos GET de lectura (`/api/v1/public/portal`, `/api/v1/public/portal/*`) a la lista permitida de `SecurityConfig`; `/api/v1/portal-settings` exige ADMIN (`@PreAuthorize`) y CSRF. La respuesta pública no incluye versión, editor ni direcciones anteriores. Los mensajes rechazan `<`/`>` en el servidor (texto simple; React además escapa).
- **QR:** generado en el navegador con `qrcode-generator` (MIT, sin dependencias, corrección de errores M, zona de silencio de 4 módulos) como SVG en pantalla/impresión y PNG por `canvas`; ningún servicio externo recibe la URL. Se cargan solo en la página de configuración (división de código por ruta). `jsqr` (Apache-2.0) es dependencia de desarrollo: las pruebas decodifican el código generado y comparan con la URL.
- **Alternativas descartadas:** columna de cantidad en `products` (rompe «catálogo global, existencias por sucursal»); recalcular precios de líneas desde el catálogo; representar «sin cargo» con precio 0; portal por sucursal o multiempresa (no existe ese modelo); redirección HTTP 301 en el servidor para alias (la SPA ya resuelve la ruta y el API devuelve el slug actual); servicio web de QR (filtra la URL a terceros).
- **Consecuencias:** el catálogo y las líneas llevan dinero exacto listo para una futura facturación sin migrar datos; editar el cobro de una línea ya registrada queda pendiente (hoy: devolución y nuevo registro); si algún día hubiera varios portales, la tabla de un registro pasa a tener clave propia y el historial de slugs ya existe.

## 30. ADR-020: identidad pública «Electrónica Rojas» y estructura del repositorio

- **Estado:** ADOPTADO · 28 de septiembre de 2026.
- **Contexto:** el proyecto se desarrolló como «BranchFix» (paquete `com.branchfix.backend`, módulo Maven en
  `backend/branchfix-backend/`, prefijos de configuración `branchfix.*`, documentos BF-*). Se publica como
  repositorio nuevo, sin historial Git previo que preservar, con la identidad «Electrónica Rojas».
- **Decisión:**
  - Nombre visible «Electrónica Rojas» (con tilde) en la interfaz y la documentación; identificadores técnicos en
    ASCII: slug `electronica-rojas`, base y usuario `electronica_rojas`, artefacto Maven
    `dev.jeffrojas:electronica-rojas-backend`, paquete `dev.jeffrojas.electronicarojas`, clase principal
    `ElectronicaRojasApplication`, documentos ER-ENG/BR/FS/ARCH/DS-001.
  - Backend en `backend/` directamente (sin la carpeta intermedia redundante); Maven Wrapper sin cambios.
  - Configuración propia bajo el prefijo neutral `app.*` (`app.security.*`, `app.bootstrap.admin.*`,
    `app.notifications.*`, `app.public.requests.*`) para no acoplarla a la marca. La variable del proxy de Vite pasa
    a `BACKEND_URL`.
  - Claves del navegador con espacio de nombres del proyecto (`electronica-rojas.theme`,
    `electronica-rojas.selectedBranch.<userId>`), porque `localhost:5173` lo comparten varios proyectos en
    desarrollo. El `Message-ID` de los avisos usa `@electronica-rojas.notifications`, que debe ser globalmente
    único y por eso no se hizo genérico.
  - `branchfix` deja de ser un slug reservado del portal: la marca es ahora el nombre del negocio y
    `electronica-rojas` es un slug natural para él.
  - **Las migraciones V1–V12 no se tocan.** Sus comentarios (y el `COMMENT ON SCHEMA` de V1) conservan referencias
    al nombre anterior; editarlas cambiaría el checksum que Flyway valida (ADR-012). La marca no justifica una
    migración nueva: no hay identificadores persistentes que dependan de ella (tablas, columnas, restricciones y
    datos son neutrales).
  - Sin clases, paquetes ni propiedades de compatibilidad con los nombres anteriores: este es el nuevo baseline.
- **Consecuencias:** una base local creada con los valores anteriores de `.env.example` no se reutiliza (nuevo
  proyecto Compose `electronica-rojas`, volumen `electronica-rojas_db-data`); la preferencia de paleta guardada en
  un navegador con la clave anterior se pierde una vez. `scripts/check-migrations.sh` y el CI siguen garantizando
  que ninguna migración aplicada cambie.

## 31. ADR-021: datos demo para el portafolio, cargados por los casos de uso reales (solo desarrollo)

- **Estado:** ADOPTADO · 28 de septiembre de 2026. No cambia reglas, endpoints, permisos ni esquema.
- **Contexto:** las capturas y la demostración necesitan una operación creíble de varias semanas en las dos
  sucursales. Un SQL manual evitaría auditoría, historial, `StockLedger`, outbox, validaciones e idempotencia, y
  podría dejar estados imposibles; además, los casos de uso sellan cada registro con el `Clock` de la aplicación y
  rechazan visitas en el pasado, así que no se puede fabricar historia «después» sin reescribir `createdAt`.
- **Decisión:** paquete `demo` (`DemoDataConfig`, `DemoDataSeeder`, `DemoScenario`) que solo existe con
  `app.demo.seed=true` (`APP_DEMO_SEED`, desactivado por defecto; `start-backend.ps1 -DemoSeed`).
  - Reproduce un guion de operaciones en orden cronológico llamando a los servicios públicos (los mismos de los
    controladores), con los DTO validados con Jakarta Validation y el colaborador real en el `SecurityContext`:
    rol, sucursal, bloqueos, idempotencia por `operationId`, historial, auditoría y outbox se aplican igual que
    en una petición. No escribe en ninguna tabla directamente; solo lee para localizar sucursales y personas.
  - `DemoClock` sustituye al reloj **solo en ese modo**: lee la hora real salvo mientras se reproduce el guion,
    cuando se mueve al instante de cada operación y sigue avanzando desde ahí. Las operaciones que aún no ocurren
    (más tarde hoy, visitas futuras) no se ejecutan: esos registros quedan en su estado actual. Los avisos salen
    del outbox con `NotificationDispatcher.runOnce()` poco después de cada evento, hacia la bandeja de desarrollo.
  - Se ejecuta como `SmartLifecycle` justo antes de que arranque el servidor web y antes de las tareas
    programadas: ninguna petición real observa el reloj simulado. Todo el guion corre en una transacción: un
    fallo no deja nada. Idempotente (si el catálogo del escenario existe, no hace nada), nunca borra ni edita
    datos existentes y solo carga sobre una base sin operaciones propias.
  - Falla cerrado si la base no es local (`localhost`) o si el correo no es la bandeja de desarrollo.
- **Alternativas descartadas:** SQL o migración con datos (salta invariantes y contaminaría el esquema de
  producción); reescribir fechas después de cargar (falsifica historial append-only); llamar a la API HTTP
  (exigiría las contraseñas de los colaboradores); perfil de Spring sin guardas (demasiado fácil de activar por
  error).
- **Consecuencias:** el escenario vive con el código y se prueba con PostgreSQL real
  (`DemoDataSeederIntegrationTests`: invariantes de existencias, transiciones, agenda, transferencias, repuestos,
  auditoría, outbox e idempotencia). Si un caso de uso cambia sus reglas, la carga demo falla en esa prueba. La
  bandeja de desarrollo vive en memoria: tras reiniciar el backend queda vacía, aunque los avisos siguen en
  *Notificaciones*. Uso y restablecimiento: `docs/runbook.md`.

## Control de cambios

Las versiones 1.0 a 1.8 se publicaron bajo el nombre anterior del proyecto (ADR-020).

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0 | 25 de septiembre de 2026 | Baseline propuesto para implementación incremental. | Jefferson Rojas Brizuela |
| 1.1 | 25 de septiembre de 2026 | Conversión a Markdown, ADR-007 (BIGINT interno, UUID para referencias públicas) y ADR-008 (implementación de sesión, CSRF y revocación en Fase 1). | Jefferson Rojas Brizuela |
| 1.2 | 26 de septiembre de 2026 | ADR-009: bloqueo ordenado, idempotencia, historial inmutable, auditoría y correlación (Fase 2). | Jefferson Rojas Brizuela |
| 1.3 | 26 de septiembre de 2026 | ADR-010 (detalles de auditoría JSONB, traducciones en la SPA, vista consolidada, bloqueo de inicio de sesión) y ADR-011 (clientes, reparaciones, máquina de estados, bloqueos e integración futura con inventario) (Fase 3). | Jefferson Rojas Brizuela |
| 1.4 | 26 de septiembre de 2026 | ADR-012: incidente de checksum de V6, restauración del V6 aplicado, V7 con el trigger que impide borrar órdenes y reglas de gestión de migraciones. | Jefferson Rojas Brizuela |
| 1.5 | 27 de septiembre de 2026 | ADR-013 (búsqueda incremental de clientes, `pg_trgm`) y ADR-014 (solicitudes a domicilio, agenda, bloqueo por técnico y restricción EXCLUDE) (Fases 3.1 y 4). | Jefferson Rojas Brizuela |
| 1.6 | 27 de septiembre de 2026 | ADR-015 (consumo de repuestos sobre `StockLedger`, orden de bloqueo, V10), ADR-016 (consentimiento por canal, *transactional outbox*, trabajador con SKIP LOCKED y *lease*, transporte de correo configurable, V11) y ADR-017 (dashboard que reutiliza los listados; navegación agrupada) (Fase 5). | Jefferson Rojas Brizuela |
| 1.7 | 27 de septiembre de 2026 | ADR-018: sistema de diseño con variables CSS, paletas por `data-theme`, componentes propios y división de código por rutas. | Jefferson Rojas Brizuela |
| 1.8 | 28 de septiembre de 2026 | ADR-019: dinero como `NUMERIC(12,2)`/`BigDecimal`, costo solo para gestión, instantánea inmutable del precio de repuestos (V12), existencias iniciales como `RECEIPT` por el `StockLedger`, portal público de un registro con historial de slugs y QR generado en el navegador. | Jefferson Rojas Brizuela |
| 2.0 | 28 de septiembre de 2026 | Versión pública como Electrónica Rojas (ER-ARCH-001). ADR-020 (identidad, estructura `backend/`, paquete `dev.jeffrojas.electronicarojas`, prefijo `app.*`, migraciones intactas); §2 estructura y dependencias reales entre módulos; §6.1 esquema actual V1–V12; §9–§10 frontend y CI tal como existen; §13 secuencia seguida; runbook movido a `runbook.md`. Los ADR-001..019 se conservan como se decidieron. | Jefferson Rojas Brizuela |
| 2.1 | 28 de septiembre de 2026 | ADR-021: datos demo para el portafolio cargados por los casos de uso reales con un reloj simulado, solo en desarrollo y con `APP_DEMO_SEED=true`. | Jefferson Rojas Brizuela |
