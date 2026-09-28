# Estándares de ingeniería

- **Documento:** ER-ENG-001
- **Versión / fecha:** 2.0 · 28 de septiembre de 2026
- **Responsable:** Jefferson Rojas Brizuela (define y mantiene estos estándares)
- **Ámbito:** todo cambio en el repositorio, lo haga una persona o un asistente de IA.

> Documento vivo. Cambiar una regla de negocio, un endpoint, un permiso, el esquema o una decisión técnica
> exige actualizar el documento correspondiente y su control de cambios.

## 1. Fuentes de verdad y jerarquía

- **ENG-001 · MUST** · Antes de modificar código, conocer `00_Contexto_Proyecto.md`, ER-BR-001, ER-FS-001 y
  ER-ARCH-001, inspeccionar el repositorio real y distinguir lo implementado, lo planificado y lo no decidido.
- **ENG-002 · MUST** · El negocio manda sobre la implementación: ER-BR-001 fija invariantes; ER-FS-001 define
  interacción, contrato HTTP y criterios de aceptación; ER-ARCH-001 registra restricciones técnicas. Jerarquía
  ante contradicciones: ER-BR > ER-FS > ER-ARCH. Un cambio incompatible se detiene y se propone una enmienda
  trazable.
- **ENG-003 · MUST** · El proyecto es un producto demostrable con datos ficticios, no una implantación comercial.
  Nunca afirmar uso en producción ni validación del negocio sin evidencia.

## 2. Principios de diseño

- **ENG-010 · MUST** · El mínimo código necesario para resolver el caso completo: legible, cohesivo,
  comprobable y sin sobreingeniería.
- **ENG-011 · MUST** · SOLID con criterio: responsabilidades claras, inyección por constructor y separación de
  HTTP, casos de uso y persistencia. Interfaces solo donde haya una frontera real o más de una implementación
  (p. ej. `MailTransport`).
- **ENG-012 · MUST** · Organización por módulo de negocio bajo `dev.jeffrojas.electronicarojas`: `audit`,
  `branches`, `customers`, `dashboard`, `inventory`, `notifications`, `repairs`, `security`, `servicerequests`,
  `users` y un `shared` mínimo. Los módulos se comunican por servicios públicos; los repositorios son privados
  de su módulo y no hay ciclos.
- **ENG-013 · MUST** · Controladores delgados: traducen HTTP/DTOs a casos de uso, validan y construyen respuestas.
  La autorización por sucursal y las transacciones viven en la capa de aplicación; `@PreAuthorize` refuerza los
  roles.
- **ENG-014 · MUST** · Sin repositorios CRUD genéricos sobre Spring Data JPA, interfaces `I*` por clase, CQRS,
  event sourcing, microservicios, brokers, JWT, Redis ni Kubernetes sin un requisito demostrado.
- **ENG-015 · MUST** · Una fuente de verdad por regla crítica: existencias (`StockLedger`), alcance por sucursal
  (`BranchService`), transiciones de reparación (`RepairPolicy`), estados de solicitudes y visitas
  (`ServicePolicy`) y consentimiento (`CustomerConsentService`).
- **ENG-016 · MUST** · Fail closed: estados desconocidos, sucursal no autorizada, entradas inválidas y cambios
  concurrentes se rechazan; los fallos se registran sin exponer datos sensibles.

## 3. Flujo de trabajo

- **ENG-021 · MUST** · Antes de un cambio no trivial: plan breve con archivos, datos afectados, riesgos, pruebas y
  decisiones pendientes.
- **ENG-023 · MUST** · Reportar solo resultados reales: qué se cambió, comandos ejecutados, pruebas aprobadas y
  fallidas, y lo que no se ejecutó. Nunca presentar una prueba no ejecutada como aprobada.
- **ENG-024 · MUST** · No reorganizar directorios, eliminar archivos, cambiar versiones ni publicar (push,
  despliegue) sin inspeccionar el estado actual y justificarlo. Commits pequeños y trazables.
- **ENG-025 · MUST** · No reescribir áreas no relacionadas; corregir la causa raíz y añadir prueba de regresión
  para bugs relevantes.
- **ENG-026 · MUST** · Uso de asistentes de IA: son herramientas de ingeniería sujetas a estos mismos estándares.
  Trabajan desde los documentos de `docs/` (resumidos en `CLAUDE.md`), no introducen secretos, no inventan
  resultados y no publican cambios. El propietario revisa, decide y responde por cada cambio que entra al
  repositorio.

## 4. Código Java y Spring

- **ENG-030 · MUST** · Java 25, Spring Boot 4.1.1 y Maven Wrapper con versiones verificadas en `pom.xml`.
  Convenciones Java, nombres expresivos, paquetes en minúsculas y UTF-8. Identificadores técnicos (paquetes,
  artefactos, base de datos, variables, rutas) solo en ASCII.
- **ENG-031 · MUST** · DTOs como `record` inmutables; nunca devolver entidades JPA por la API ni exponer campos
  internos por serialización accidental.
- **ENG-032 · MUST** · Jakarta Validation en los límites HTTP y reglas de negocio en servicios. Errores como
  `ProblemDetail` (RFC 9457) con códigos estables y sin stack traces.
- **ENG-033 · MUST** · `@Transactional` en casos de uso compuestos, `readOnly = true` en consultas. El método se
  invoca a través del proxy de Spring. La auditoría y los escritos entre módulos usan `Propagation.MANDATORY`
  para existir solo dentro de la transacción del caso de uso.
- **ENG-034 · MUST** · Flyway versionado y `ddl-auto=validate`. Una migración aplicada en cualquier base,
  incluida la local, no se edita: todo cambio va en una versión nueva (ER-ARCH-001 ADR-012).
- **ENG-035 · MUST** · El `.env` de Docker Compose no configura el proceso Java arrancado desde PowerShell;
  `scripts/start-backend.ps1` deriva `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` sin imprimirlos.
- **ENG-036 · MUST** · Configuración propia bajo el prefijo neutral `app.*` (`app.security.*`, `app.bootstrap.*`,
  `app.notifications.*`, `app.public.*`), sin acoplarla a la marca.

## 5. Seguridad

- **SEC-001 · MUST** · Autenticación estándar de Spring Security: hash adaptativo (BCrypt), sesión de servidor,
  cookie HttpOnly (Secure con HTTPS), expiración y cierre real de sesión. Prohibido el hash o la autenticación
  caseros.
- **SEC-002 · MUST** · CSRF activo para toda mutación desde la SPA (cookie `XSRF-TOKEN` + cabecera
  `X-XSRF-TOKEN`), renovado al iniciar y cerrar sesión. Nunca desactivarlo para hacer pasar pruebas.
- **SEC-003 · MUST** · Autorización en el servidor por rol y sucursal en toda lectura o escritura sensible.
  Ocultar opciones en React nunca es autorización.
- **SEC-004 · MUST** · Sin registro público de colaboradores ni credenciales fijas. El primer administrador se
  crea solo con variables de entorno (`BOOTSTRAP_ADMIN_*`).
- **SEC-005 · MUST** · Nunca versionar contraseñas, tokens, `.env`, respaldos ni datos personales reales. Nunca
  imprimir secretos ni datos privados en logs.
- **SEC-006 · MUST** · Endpoints anónimos limitados a los listados en ER-FS-001; health sanitizado; límites de
  abuso en formularios públicos. TLS y política CORS restrictiva antes de cualquier piloto.
- **SEC-007 · MUST** · Consultas parametrizadas/JPA contra inyección SQL, sin HTML no confiable contra XSS y DTOs
  explícitos contra asignación masiva.

## 6. Integridad, concurrencia e idempotencia

- **DATA-001 · MUST** · Claves foráneas, UNIQUE, CHECK y NOT NULL en PostgreSQL refuerzan los invariantes como
  segunda línea de defensa.
- **DATA-002 · MUST** · Una transferencia descuenta el origen, suma el destino y registra ambos movimientos en UNA
  transacción, con bloqueo que impida saldos negativos bajo concurrencia.
- **DATA-003 · MUST** · Los comandos que cambian existencias o crean registros de custodia llevan un
  `operationId` del cliente; un reintento no duplica efectos. No depender de deshabilitar el botón.
- **DATA-004 · MUST** · Bloquear filas en orden determinista cuando se tocan varias, y probarlo con peticiones
  concurrentes reales.
- **DATA-005 · MUST** · El historial (movimientos, estados, auditoría) es append-only; desactivar o archivar en
  lugar de borrar cuando hay historial comercial o custodia.
- **DATA-006 · MUST** · Instantes en UTC en base de datos y API; presentación en America/Costa_Rica. Cantidades
  enteras; dinero como `BigDecimal` / `NUMERIC(12,2)`, nunca `float`/`double`.

## 7. Pruebas, CI y observabilidad

- **TST-001 · MUST** · JUnit 5/Mockito para reglas puras; MockMvc y Spring Security Test para contratos HTTP y
  seguridad; Testcontainers con PostgreSQL 17 real para Flyway, restricciones, bloqueos y transacciones. Nunca H2
  para probar comportamiento de PostgreSQL.
- **TST-002 · MUST** · El CI compila y prueba backend y frontend y reporta fallos genuinos. No se deshabilita
  `contextLoads` ni se marca `@Disabled` para ocultar configuración.
- **TST-003 · MUST** · Pruebas negativas de autorización obligatorias: usuario de otra sucursal, rol sin
  facultad, acceso anónimo y mutación sin CSRF.
- **TST-004 · MUST** · Toda operación de existencias prueba caso feliz, insuficiencia, rol incorrecto,
  idempotencia, concurrencia y rollback tras fallo simulado.
- **CI-001 · MUST** · GitHub Actions: `./mvnw -B verify` (Java 25 + Testcontainers), `npm ci`, lint, pruebas,
  contraste WCAG y build; falla si se versiona un `.env` o si cambia una migración ya existente.
- **OBS-001 · MUST** · Cada línea de log lleva el identificador de correlación (`X-Request-Id`); nunca
  contraseñas, hashes, cookies, cuerpos completos ni datos personales.
- **OBS-002 · MUST** · Auditoría funcional separada del log técnico: actor, sucursal, acción, entidad, instante
  UTC, motivo y `operationId` cuando aplique.

## 8. Interfaz, accesibilidad y rendimiento

- **UX-001 · MUST** · React + TypeScript con componentes reutilizables del sistema de diseño (ER-DS-001); estados
  de carga, vacío y error; feedback específico por acción.
- **UX-002 · MUST** · Bloquear duplicados visualmente como ayuda, sin sustituir la idempotencia del servidor.
  Confirmar solo acciones destructivas o de custodia.
- **UX-003 · MUST** · Listados con paginación, búsqueda y filtros en el servidor; índices según consultas reales.
- **UX-004 · MUST** · WCAG 2.2 AA: contraste verificado por `npm run check:contrast`, foco visible, objetivos
  táctiles de 44 px en móvil, estados con texto (nunca solo color) y navegación por teclado.

## 9. Definition of Done

- El código compila sin errores nuevos y las pruebas del cambio se ejecutaron; los riesgos pendientes están
  declarados.
- Autorización, CSRF, validación y reglas de negocio comprobadas con casos positivos y negativos.
- Migraciones nuevas revisadas; ninguna migración existente modificada.
- README y comandos replicables; ningún secreto en el repositorio.
- La interfaz permite probar el flujo o se marca explícitamente como pendiente.
- ER-BR / ER-FS / ER-ARCH / trazabilidad actualizados cuando el cambio afecta un contrato.

## 10. Comunicación de resultados

| No decir | En su lugar |
|---|---|
| «Los tests están bien» sin ejecutarlos | «Ejecuté `./mvnw -B verify`: X aprobadas, Y fallidas», con el comando. |
| «Spring maneja todo automáticamente» | Qué se autoconfigura, por qué y bajo qué condición. |
| «El sistema es seguro» | Qué pruebas negativas de autorización, CSRF y ausencia de secretos lo respaldan. |
| «Está listo para producción» | Separar demo, piloto y producción (backups, TLS y restauración probada). |

## Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0 | 25 de septiembre de 2026 | Baseline propuesto para implementación incremental. | Jefferson Rojas Brizuela |
| 2.0 | 28 de septiembre de 2026 | Versión pública como Electrónica Rojas (ER-ARCH-001 ADR-020). Se retira la metodología privada de aprendizaje (ENG-020 y ENG-022, y el formato de entrega de la antigua sección 11); se añaden ENG-026 (uso de asistentes de IA), ENG-036 (prefijo de configuración `app.*`), CI-001 y UX-004; ENG-012 refleja los módulos reales. | Jefferson Rojas Brizuela |
