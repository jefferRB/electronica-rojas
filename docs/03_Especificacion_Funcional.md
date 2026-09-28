# Especificación funcional

*Pantallas, flujos, contrato HTTP, permisos y criterios de aceptación*

- **Documento:** ER-FS-001
- **Versión / fecha:** 2.0 · 28 de septiembre de 2026
- **Responsable:** Jefferson Rojas Brizuela
- **Estado:** refleja lo que existe en el código y en las pruebas a esta fecha. Las reglas de negocio que
  implementa siguen siendo propuestas (ER-BR-001 §0).

> Documento vivo. Cambiar un endpoint, un permiso o una pantalla exige actualizar este documento, su control de
> cambios y, si corresponde, `05_Trazabilidad_Requisitos.md`.

## 1. Visión y alcance

SPA administrativa React para una electrónica de venta y reparación con varias sucursales: vistas por sucursal,
trazabilidad de existencias, seguimiento de equipos en custodia, agenda de técnicos y un portal público acotado
para solicitudes a domicilio. El diseño admite N sucursales sin convertirse en SaaS multiempresa.

Estados usados en este documento: **IMPLEMENTED** (código + pruebas automatizadas), **PARTIAL** (la nota dice qué
falta) y **PENDING** (sin implementar).

## 2. Estado por módulo

| Módulo | Estado | Qué existe | Qué falta | Evidencia principal |
|---|---|---|---|---|
| Identidad y acceso | IMPLEMENTED | Sesión de servidor, CSRF SPA, roles, alcance por sucursal, revocación de sesión, bloqueo de intentos de login, bootstrap del primer admin | SSO, autoservicio de contraseña | `AuthenticationIntegrationTests`, `BranchAccessIntegrationTests`, `LoginThrottlingIntegrationTests` |
| Sucursales y usuarios | IMPLEMENTED | CRUD de sucursales y colaboradores, último admin protegido | Flujo de cierre de sucursal con operaciones pendientes | `UserAdministrationIntegrationTests` |
| Catálogo y existencias | IMPLEMENTED | Catálogo global, precios y costo, existencias por sucursal, mínimos, estados, vista consolidada | Stock comprometido, compras | `ProductCatalogIntegrationTests`, `StockOverviewIntegrationTests` |
| Movimientos y transferencias | IMPLEMENTED | Entradas, salidas, ajustes, transferencias inmediatas atómicas e idempotentes | Transferencias en tránsito (BR-TRF-006) | `StockTransferIntegrationTests`, `InventoryConcurrencyIntegrationTests` |
| Auditoría | IMPLEMENTED | Eventos append-only con detalles estructurados y filtros | Exportación | `AuditIntegrationTests` |
| Clientes | IMPLEMENTED | Búsqueda incremental, resolución de identidad, consentimiento por canal | Reconfirmación de consentimiento al cambiar el correo (decisión abierta) | `CustomerLookupIntegrationTests`, `CustomerConsentIntegrationTests` |
| Reparaciones | IMPLEMENTED | Recepción idempotente, máquina de estados, técnico, diagnóstico, cotizaciones, entrega única, comprobante imprimible | Fotografías, consulta pública de la orden (FR-NOT-002) | `RepairOrderIntegrationTests`, `RepairConcurrencyIntegrationTests` |
| Repuestos en reparaciones | IMPLEMENTED | Consumo y devoluciones sobre el mismo mecanismo de existencias, precio instantáneo y subtotal | Editar el cobro de una línea registrada | `RepairPartIntegrationTests`, `RepairPartConcurrencyIntegrationTests` |
| Servicio a domicilio | IMPLEMENTED | Portal público configurable, bandeja, visitas, agenda, horarios, flujo del técnico, paso a taller | Cálculo de traslados, mapas, reprogramación por el cliente | `VisitSchedulingIntegrationTests`, `VisitConcurrencyIntegrationTests`, `PortalSettingsIntegrationTests` |
| Avisos al cliente | PARTIAL | Outbox transaccional, trabajador con reintentos, plantillas, bandeja de desarrollo, administración | Proveedor SMTP autorizado (preparado, desactivado), SPF/DKIM, WhatsApp real | `NotificationOutboxIntegrationTests` |
| Dashboard | IMPLEMENTED | Indicadores por sucursal o consolidados, prioridades, próximas visitas, acciones rápidas | Tendencias y reportes | `DashboardIntegrationTests` |
| Reportes | PARTIAL | Existencias por sucursal y movimientos filtrados por producto y tipo | Filtro por fechas en movimientos, exportación CSV | `StockMovementIntegrationTests` |
| Comercial | PENDING | — | Ventas, facturación electrónica, pagos | — |

## 3. Roles y matriz de permisos

| Función | ADMIN | BRANCH_MANAGER | RECEPTIONIST | TECHNICIAN |
|---|---|---|---|---|
| Sucursales / usuarios | Administrar | Ver asignadas | Ver asignadas | Ver asignadas |
| Catálogo: precios y existencias iniciales | Administrar | Ver | Ver precio | Precio vía su orden |
| Costo de productos y repuestos | Ver | Ver | No | No |
| Existencias y movimientos | Todas | Operar asignadas | Leer asignadas | No (solo repuestos de su orden) |
| Transferencias | Todas | Si opera origen **y** destino | No | No |
| Clientes | Todos | De sus sucursales | De sus sucursales | No (solo el nombre en sus órdenes) |
| Preferencias de avisos del cliente | Todos | De sus sucursales | De sus sucursales | No |
| Recepción / entrega de equipos | Todas | Asignadas | Asignadas | Ver asignadas |
| Diagnóstico y estados técnicos | Todas | Supervisar | No | Órdenes asignadas |
| Asignar técnico | Todas | Asignadas | No | No |
| Cotización: emitir / decidir | Ambas | Ambas | Solo decidir | Solo emitir (órdenes asignadas) |
| Repuestos: registrar | Todas | Asignadas | No (consulta) | Órdenes asignadas en reparación |
| Repuestos: corregir | Todas | Asignadas | No | Órdenes asignadas abiertas |
| Repuestos: otro precio o sin cargo | Todas | Asignadas | No | No |
| Solicitudes a domicilio | Todas | Asignadas | Gestionar asignadas | No |
| Programar / confirmar visitas | Todas | Asignadas | Asignadas | No |
| Horarios de técnicos | Todos | Sucursales asignadas | Consulta | No |
| Iniciar / cerrar visita | Sí | Sí | No | Solo sus visitas |
| Portal público: configuración | Administrar | No | No | No |
| Avisos: estados, fallos y reintentos | Todas | Asignadas | No | No |
| Dashboard | Sucursal o consolidado | Sucursal o consolidado | Sucursal seleccionada | Trabajo propio |
| Auditoría | Global | Sus sucursales | No | No |

Ocultar una opción en la SPA es solo UX (`frontend/src/features/auth/permissions.ts`); cada regla se aplica en el
servidor.

## 4. Requisitos funcionales

### 4.1 Navegación y accesibilidad

- **FR-NAV-001 · MUST · IMPLEMENTED** · Layout autenticado con marca, selector de sucursal autorizada, menú de
  cuenta y navegación por rol: Inicio, Reparaciones, A domicilio, Agenda (o Mis visitas), Clientes e Inventario;
  *Más* agrupa Transferencias, Notificaciones, Auditoría, Sucursales y Usuarios.
- **FR-NAV-002 · MUST · IMPLEMENTED** · Responsive escritorio/tableta/móvil (barra flotante, menú lateral modal y
  barra inferior), tablas que se vuelven tarjetas, etiquetas visibles, foco de teclado y estados con texto.
- **FR-NAV-003 · MUST · IMPLEMENTED** · Carga, vacío, conflicto, sin permiso y error de red distinguibles; filtros
  de los listados en la URL, conservados al volver del detalle.
- **FR-NAV-004 · MUST · IMPLEMENTED** · El selector de sucursal cambia el contexto visual; cada API revalida la
  sucursal pedida contra los permisos efectivos.

### 4.2 Autenticación y administración

- **FR-AUTH-001 · MUST · IMPLEMENTED** · Login por correo y contraseña; sesión HttpOnly; mensaje neutro para
  credenciales inválidas, sin revelar si la cuenta existe.
- **FR-AUTH-002 · MUST · IMPLEMENTED** · Logout con POST + CSRF; invalida la sesión y limpia las cachés privadas de
  la SPA. GET no cierra sesión.
- **FR-AUTH-003 · MUST · IMPLEMENTED** · 401 para anónimos, 403 para funciones no permitidas por rol, 404
  indistinguible para recursos fuera del alcance de sucursal.
- **FR-AUTH-004 · MUST · IMPLEMENTED** · Alta y desactivación de usuarios, roles, sucursales y restablecimiento de
  contraseña por ADMIN. Sin registro público.
- **FR-BRH-001 · MUST · PARTIAL** · ADMIN lista, crea, edita, activa y desactiva sucursales. *Falta:* impedir la
  desactivación cuando hay operaciones pendientes (hoy la sucursal desactivada deja de admitir operaciones nuevas
  y conserva su historial, BR-BRH-001).
- **FR-BRH-002 · MUST · IMPLEMENTED** · Colaborador con nombre, correo, rol, sucursales y estado; un técnico puede
  tener varias sucursales sin acceso general al inventario.

### 4.3 Dashboard

- **FR-DSH-001 · MUST · PARTIAL** · Indicadores por sucursal: reparaciones por etapa, solicitudes pendientes y en
  revisión, visitas de hoy y próximas, productos sin existencias y bajo mínimo. *Falta:* indicador de
  transferencias recientes.
- **FR-DSH-002 · MUST · IMPLEMENTED** · Cada indicador abre su listado con el mismo filtro; el conteo es el
  `totalElements` de ese listado y nunca suma sucursales fuera del alcance.
- **FR-DSH-003 · MUST · IMPLEMENTED** · Acciones rápidas según el rol; no se ofrecen acciones que el usuario no
  puede ejecutar.

### 4.4 Inventario y transferencias

- **FR-INV-001 · MUST · IMPLEMENTED** · Existencias paginadas por sucursal: SKU, nombre, categoría, cantidad,
  mínimo, estado y última actualización; búsqueda y filtros.
- **FR-INV-002 · MUST · IMPLEMENTED** · Detalle de producto con existencias en las sucursales visibles y acciones
  por rol; los inactivos siguen consultables.
- **FR-INV-003 · MUST · IMPLEMENTED** · Formulario de producto con SKU único, categoría, tipo, descripción,
  precios y existencias iniciales opcionales; errores por campo.
- **FR-INV-004 · MUST · IMPLEMENTED** · Entrada, salida o ajuste con sucursal, cantidad positiva y motivo; muestra
  saldo anterior y nuevo.
- **FR-TRF-001 · MUST · IMPLEMENTED** · Transferencia en pasos Seleccionar → Revisar → Confirmar con saldos de
  origen y destino antes y después.
- **FR-TRF-002 · MUST · IMPLEMENTED** · `operationId` por intención del usuario, conservado en reintentos por error
  de red.
- **FR-TRF-003 · MUST · IMPLEMENTED** · Insuficiencia como conflicto comprensible con el saldo disponible, sin
  cambios parciales.
- **FR-TRF-004 · MUST · IMPLEMENTED** · Resultado y detalle con ambos movimientos y el actor.
- **FR-TRF-005 · MUST · IMPLEMENTED** · Rechazo de sucursales iguales, no autorizadas o inactivas.
- **FR-RPT-001 · MUST · PARTIAL** · Existencias por sucursal (y consolidadas) y movimientos filtrados por producto y
  tipo. *Falta:* rango de fechas en movimientos y exportación CSV.
- **FR-AUD-001 · MUST · IMPLEMENTED** · Auditoría visible a ADMIN y BRANCH_MANAGER con actor, instante en hora
  local, motivo y referencia; filtros por acción, fechas y sucursal; no editable.

### 4.5 Clientes y reparaciones

- **FR-CUS-001 · MUST · IMPLEMENTED** · Listado, alta y edición de clientes con contacto validado y su historial
  (taller y domicilio) limitado por autorización; sin fusiones automáticas.
- **FR-REP-001 · MUST · IMPLEMENTED** · Recepción en pasos: cliente (existente o nuevo), sucursal, equipo, falla,
  accesorios y condición; orden en RECEIVED en una transacción.
- **FR-REP-002 · MUST · IMPLEMENTED** · Listado filtrable por sucursal, estado, técnico, cliente, fechas y texto;
  ficha con historial inmutable y asignación de técnico.
- **FR-REP-003 · MUST · IMPLEMENTED** · Vista del técnico: órdenes asignadas y acciones permitidas calculadas por el
  servidor (`actions`).
- **FR-REP-004 · MUST · IMPLEMENTED** · READY_FOR_PICKUP muestra el estado del aviso por separado; la entrega es un
  acto explícito con quién y cuándo.
- **FR-REP-005 · MUST · IMPLEMENTED** · Las órdenes CANCELLED o UNREPAIRABLE sin entregar se muestran como
  «pendiente de devolver» y ofrecen «Registrar entrega al cliente».
- **FR-REP-006 · SHOULD · IMPLEMENTED** · Comprobante de recepción imprimible (`/repairs/{id}/receipt`) sin
  diagnóstico, cotizaciones, técnico ni notas internas.

### 4.6 Servicio a domicilio y avisos

- **FR-SRV-001 · MUST · IMPLEMENTED** · Formulario público en `/solicitar/<slug>` con contacto, equipo, problema,
  dirección, preferencia y consentimientos.
- **FR-SRV-002 · MUST · IMPLEMENTED** · Confirmación «solicitud recibida, pendiente de confirmación», nunca «cita
  reservada»; página de estado por referencia no adivinable.
- **FR-SRV-003 · MUST · IMPLEMENTED** · Bandeja interna con filtros, resolución de identidad, programación y
  confirmación manual con historial.
- **FR-SRV-004 · MUST · PARTIAL** · Límites de frecuencia, campo trampa, idempotencia, límites de tamaño y ninguna
  enumeración de clientes. *Falta:* texto de privacidad revisado legalmente antes de publicar en Internet.
- **FR-NOT-001 · SHOULD · PARTIAL** · Avisos por correo solo con consentimiento, con estado de envío separado del
  estado de negocio, reintentos y fallos registrados. *Falta:* proveedor real (hoy bandeja de desarrollo).
- **FR-NOT-002 · SHOULD · PENDING** · Enlace público no adivinable para que el cliente consulte **su orden de
  reparación**. (Las solicitudes a domicilio ya tienen consulta por UUID.)

## 5. Contrato HTTP

### 5.1 Convenciones

| Categoría | Contrato |
|---|---|
| Versionado | Todo bajo `/api/v1`. |
| Sesión y CSRF | Todo endpoint no listado como público exige sesión (401 anónimo). Las mutaciones exigen la cabecera `X-XSRF-TOKEN` igual a la cookie `XSRF-TOKEN` (403 si falta o no coincide), también las públicas. |
| Autorización | 403 para funciones no permitidas por rol; 404 idéntico para recursos fuera del alcance de sucursal o inexistentes. |
| Validación | DTOs con Jakarta Validation; 400 con `errors[]` (`field`, `code`, `message`). |
| Conflicto | 409 con `code` estable: existencias insuficientes, `OPERATION_ID_REUSED`, `STALE_VERSION`, `INVALID_TRANSITION`, `SCHEDULE_CONFLICT`, bloqueo no obtenido (reintentable con el mismo `operationId`). |
| Errores | `ProblemDetail` (RFC 9457) sin stack traces; la SPA traduce los códigos (`frontend/src/shared/i18n`). |
| Idempotencia | `operationId` (UUID) generado por el cliente una vez por intención: 201 la primera vez, 200 con la misma respuesta al repetir. |
| Paginación | `page`/`size` con `size` ≤ 100 y orden estable; respuesta `PageResponse`. |
| Fechas | ISO 8601 UTC en la API; America/Costa_Rica en la interfaz y en los filtros por día. |
| Privacidad | Nunca `passwordHash`, tokens, entidades JPA completas ni datos de contacto a roles que no los necesitan. |

Endpoints anónimos (lista cerrada, `SecurityConfig`): `GET /actuator/health` (solo `{"status":"UP"}`),
`GET /api/v1/ping`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`, `GET /api/v1/public/branches`,
`GET /api/v1/public/portal`, `GET /api/v1/public/portal/{slug}`, `POST /api/v1/public/service-requests` y
`GET /api/v1/public/service-requests/{publicRef}`.

### 5.2 Identidad y sesión

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/auth/csrf | Público | 204; emite la cookie `XSRF-TOKEN` (legible por la SPA, SameSite=Lax). |
| POST /api/v1/auth/login | Público + CSRF; form `email`, `password` | 204 con sesión nueva (id rotado, token CSRF renovado); 401 con el mismo mensaje neutro para correo inexistente, contraseña errónea o cuenta desactivada. Tras 5 fallos por cuenta o 20 por IP en 15 minutos, 429 con `Retry-After` durante 15 minutos (también para correos inexistentes). |
| POST /api/v1/auth/logout | CSRF | 204; invalida la sesión y borra `JSESSIONID`. |
| GET /api/v1/auth/me | Autenticado | 200; usuario (id, correo, nombre, rol) y sucursales activas seleccionables. |
| GET /api/v1/auth/password-policy | Autenticado | 200 `{minLength, maxBytes}`, la misma política que aplica el servidor. |

Cambiar el rol, desactivar la cuenta o restablecer la contraseña revoca las sesiones abiertas en la siguiente
petición; retirar una sucursal surte efecto inmediato sin cerrar sesión. El primer administrador solo se crea con
`BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD`, de forma idempotente y sin modificar cuentas existentes.

### 5.3 Sucursales y usuarios

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/branches | Autenticado | ADMIN: todas (incluidas inactivas); otros roles: asignadas y activas. |
| GET /api/v1/branches/{id} | Autenticado | 200 en su alcance; 404 idéntico para sucursal ajena o inexistente. |
| POST /api/v1/branches | ADMIN + CSRF | 201; código normalizado a mayúsculas; 409 código duplicado. |
| PUT /api/v1/branches/{id} | ADMIN + CSRF | 200; nombre, dirección, activo y `version` (409 si está desactualizada). |
| GET /api/v1/users?page&size | ADMIN | `PageResponse`, orden por correo. |
| GET /api/v1/users/{id} | ADMIN | 200 / 404. |
| POST /api/v1/users | ADMIN + CSRF | 201; 409 correo duplicado sin distinguir mayúsculas; 400 contraseña < 12 caracteres o > 72 bytes, rol no ADMIN sin sucursal activa. |
| PUT /api/v1/users/{id} | ADMIN + CSRF | 200; nombre, rol, activo, sucursales y `version`; 409 si deja al sistema sin administrador activo. |
| POST /api/v1/users/{id}/password-reset | ADMIN + CSRF | 204; revoca las sesiones del usuario. |

### 5.4 Catálogo, existencias y vista consolidada

Lectura de inventario: ADMIN, BRANCH_MANAGER y RECEPTIONIST; operación: ADMIN y BRANCH_MANAGER; catálogo: ADMIN;
TECHNICIAN 403.

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/products?search&category&kind&status&page&size | Lectura | `PageResponse<ProductResponse>`, orden por SKU; `status` ACTIVE (defecto), INACTIVE o ALL. Incluye `salePrice`, `chargeableByDefault` y `unitCost` (este último solo para ADMIN y BRANCH_MANAGER). |
| GET /api/v1/products/categories | Lectura | Categorías existentes. |
| GET /api/v1/products/{id} | Lectura | Producto + existencias en las sucursales visibles; 404. |
| POST /api/v1/products | ADMIN + CSRF | 201; SKU normalizado y único (409); `unitCost?`, `salePrice?` (≥ 0, dos decimales), `chargeableByDefault?`, `initialStock?: {branchId, quantity}`: con cantidad > 0 registra un `RECEIPT` en la misma transacción; sucursal inválida 400 `BRANCH_INVALID` y no se crea nada. |
| PUT /api/v1/products/{id} | ADMIN + CSRF | 200; datos, precios y `version`; SKU inmutable; auditoría `PRODUCT_PRICING_CHANGED` con antes y después. |
| GET /api/v1/branches/{id}/stock?search&category&kind&status&stockStatus&page&size | Lectura en la sucursal | Todo el catálogo filtrado con la cantidad de la sucursal (0 si nunca tuvo), mínimo, `stockStatus` (OUT_OF_STOCK, LOW, NORMAL) y última actualización; filtro `stockStatus` = OUT_OF_STOCK, LOW o ATTENTION. |
| PUT /api/v1/branches/{id}/stock/{productId}/minimum | Operación + CSRF | 200; `minimumQuantity` ≥ 0. |
| GET /api/v1/stock/overview?search&category&kind&status&stockStatus&page&size | Lectura | `{branches, rows}`: una columna por sucursal activa autorizada (alcance en SQL), total, conteos por estado y celdas; dos consultas por página (sin N+1). |

### 5.5 Movimientos y transferencias

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/branches/{id}/movements?productId&type&page&size | Lectura en la sucursal | Historial inmutable, más reciente primero, con saldo anterior y posterior, actor y referencia a transferencia u orden (`repairOrderCode`). ADMIN también consulta sucursales inactivas. |
| POST /api/v1/stock-movements | Operación + CSRF | 201 / 200 al repetir el `operationId`. Tipos `RECEIPT` (+), `ISSUE` (−), `ADJUSTMENT_IN` (+), `ADJUSTMENT_OUT` (−); cantidad positiva (máx. 1 000 000) y motivo. 409 insuficiencia (`available`, `requested`, `branchId`), producto inactivo u `operationId` reutilizado. |
| POST /api/v1/stock-transfers | ADMIN, o BRANCH_MANAGER de origen **y** destino + CSRF | 201 / 200 al repetir; 400 misma sucursal; 404 sucursal no autorizada, inactiva o inexistente; 409 insuficiencia, producto inactivo, `operationId` reutilizado o bloqueo no obtenido. |
| GET /api/v1/stock-transfers/{id} | ADMIN o BRANCH_MANAGER de origen o destino | Cabecera, ambos movimientos y actor; 404 fuera de alcance; 403 otros roles. |

`TRANSFER_OUT`/`TRANSFER_IN` solo los crea una transferencia; `OUT_FOR_REPAIR`/`RETURN_FROM_REPAIR`, solo el
módulo de reparaciones (§5.9).

### 5.6 Auditoría

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/audit-events?branchId&entityType&action&from&to&page&size | ADMIN (todo) o BRANCH_MANAGER (sus sucursales) | `PageResponse` con `details` estructurados y `detailsSource` (RECORDED, LEGACY, NONE); `from`/`to` son días de Costa Rica inclusivos (400 `DATE_RANGE_INVALID` si `to` < `from`). |

La auditoría registra actor, acción, entidad, sucursal, `operationId`, correlación (`X-Request-Id`) y un resumen
sin datos personales; una operación fallida no deja evento.

### 5.7 Clientes y consentimiento

Acceso: ADMIN, BRANCH_MANAGER y RECEPTIONIST con alcance BR-CUS-003; TECHNICIAN 403.

| HTTP | Respuesta |
|---|---|
| GET /api/v1/customers?search&page&size | Clientes de su ámbito; búsqueda por nombre, correo o dígitos del teléfono. |
| GET /api/v1/customers/lookup?name&phone&size | `{candidates, moreInScope}` desde 3 letras o 3 dígitos (`size` ≤ 10); con teléfono completo, además `{id, fullName, inScope:false}` de otras sucursales. |
| GET /api/v1/customers/phone-matches?phone | `[{id, fullName, inScope}]` del teléfono exacto normalizado en toda la empresa; 400 `PHONE_INVALID`. |
| GET /api/v1/customers/{id} | 200 / 404. |
| POST /api/v1/customers + CSRF | 201; `{branchId, customer:{fullName, phone, email?, address?, internalNotes?, allowDuplicatePhone?}, consent?}`; teléfono en E.164; 409 `POSSIBLE_DUPLICATE_CUSTOMER` con `matches[].matchedBy` (PHONE, NAME, PHONE_AND_NAME). |
| PUT /api/v1/customers/{id} + CSRF | 200; datos y `version` (409 `STALE_VERSION`). |
| GET /api/v1/customers/{id}/consents | `{hasEmail, currentTextVersion, channels:[{channel, latest}], history}`. |
| POST /api/v1/customers/{id}/consents + CSRF | `{channel: EMAIL\|WHATSAPP, granted, source: IN_PERSON\|PHONE\|WRITTEN, textVersion?}`; 400 `CONSENT_TEXT_OUTDATED`, `CONSENT_SOURCE_INVALID`; 409 `EMAIL_REQUIRED`. |

### 5.8 Reparaciones

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/repair-orders?branchId&status&technicianId&customerId&search&from&to&page&size | Autenticado | Más recientes primero; el técnico solo recibe las asignadas; `search` en código, cliente, marca y serie. |
| GET /api/v1/repair-orders/{id} | Con acceso a la orden | Detalle: cliente (sin teléfono ni correo para técnicos), equipo, recepción, técnico, diagnóstico, entrega, `history`, `quotes`, `parts`, `partsSummary`, `notifications` y `actions` calculadas por el servidor. |
| POST /api/v1/repair-orders | ADMIN, BRANCH_MANAGER, RECEPTIONIST + CSRF | 201. `{operationId, branchId, customerId? , customerPhone?, newCustomer?, deviceType, brand, model?, serialNumber?, reportedFault, physicalCondition, accessories?}`; exactamente uno de `customerId` o `newCustomer` (400 `CUSTOMER_REQUIRED`); un cliente fuera de su ámbito exige `customerPhone` igual al registrado. Idempotente por `operationId`. |
| POST /api/v1/repair-orders/{id}/status | Según BR-REP-009 + CSRF | `{toStatus, reason?}`; 409 `INVALID_TRANSITION` (con `fromStatus`/`toStatus`), 403 por rol, 400 `REASON_REQUIRED`, 409 `TECHNICIAN_REQUIRED`. |
| PUT /api/v1/repair-orders/{id}/technician | ADMIN, BRANCH_MANAGER + CSRF | `{technicianId}`; 400 `TECHNICIAN_NOT_ELIGIBLE`; 409 `ORDER_CLOSED`. |
| GET /api/v1/repair-orders/technicians?branchId | ADMIN, BRANCH_MANAGER | Técnicos activos asignados a la sucursal. |
| PUT /api/v1/repair-orders/{id}/diagnosis | ADMIN, BRANCH_MANAGER, técnico asignado + CSRF | `{diagnosis, version}`; 409 `STALE_VERSION` u `ORDER_CLOSED`. |
| POST /api/v1/repair-orders/{id}/quotes | ADMIN, BRANCH_MANAGER, técnico asignado + CSRF | `{amount, description}` (CRC, > 0, dos decimales); orden a AWAITING_APPROVAL; 409 `QUOTE_PENDING_EXISTS` o `QUOTE_NOT_ALLOWED`. |
| POST /api/v1/repair-orders/{id}/quotes/{quoteId}/decision | ADMIN, BRANCH_MANAGER, RECEPTIONIST + CSRF | `{decision: APPROVED\|REJECTED, method: IN_PERSON\|PHONE\|EMAIL\|MESSAGE, note?}`; 409 `QUOTE_ALREADY_DECIDED`. |

### 5.9 Repuestos en reparaciones

Misma regla de acceso que la orden (404 fuera de alcance).

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/repair-orders/{id}/parts/options?search&page&size | ADMIN, BRANCH_MANAGER, técnico asignado | Repuestos activos de la sucursal de la orden con existencias, `stockStatus`, `salePrice` y `chargeableByDefault` (nunca el costo). |
| POST /api/v1/repair-orders/{id}/parts | Ídem + CSRF | 201 / 200 al repetir. `{operationId, productId, quantity (1..1000), note?, unitPrice?, chargeable?}`. Otro precio o cobro que el del catálogo: solo ADMIN y BRANCH_MANAGER (403 al técnico, sin tocar existencias). 409 `PARTS_NOT_ALLOWED` (fuera de `IN_REPAIR`), `INSUFFICIENT_STOCK`, `PRODUCT_INACTIVE`, `OPERATION_ID_REUSED`; 400 `NOT_A_SPARE_PART`. |
| POST /api/v1/repair-orders/{id}/parts/{usageId}/returns | Orden abierta: ADMIN, BRANCH_MANAGER, técnico asignado; cerrada: ADMIN, BRANCH_MANAGER + CSRF | 201 / 200 al repetir. `{operationId, quantity, reason}`; 409 `RETURN_EXCEEDS_CONSUMED` (`consumed`, `returned`, `remaining`). |

`parts[]` incluye `unitPrice`, `unitCost` (solo gestión), `chargeable`, `priceOverridden` y `chargedAmount`;
`partsSummary` incluye `chargeableSubtotal`, `unpricedLines` y `totalCost` (solo gestión). El subtotal se muestra
aparte de la cotización.

### 5.10 Servicio a domicilio: superficie pública

| HTTP | Respuesta |
|---|---|
| GET /api/v1/public/portal/{slug} | `{slug, accepting, welcomeMessage, successMessage, rules, branches}` con el slug actual (también al entrar por uno anterior); fechas ya calculadas en hora de Costa Rica. Pausado: `accepting=false`, sin reglas ni sucursales. 404 para un slug desconocido. |
| GET /api/v1/public/portal | Lo mismo para el portal actual (enlaces antiguos a `/solicitar-servicio`). |
| GET /api/v1/public/branches | `[{id, name}]` de sucursales activas. |
| POST /api/v1/public/service-requests (+ CSRF) | 202 `{publicRef, requestCode, status: RECEIVED}`; idempotente por `submissionId`; 400 con códigos (`PHONE_INVALID`, `BRANCH_INVALID`, `PROVINCE_NOT_SERVED`, `WINDOW_NOT_ALLOWED`, `DATE_NOT_ALLOWED`, `DATE_TOO_SOON`, `DATE_TOO_FAR`, `DAY_NOT_SERVED`, `EMAIL_REQUIRED_FOR_CONSENT`, `CONSENT_TEXT_OUTDATED`); 409 `PORTAL_DISABLED` (un reintento de un envío aceptado sigue devolviendo su comprobante); 429 `RATE_LIMITED` con `Retry-After`. Un campo trampa (`website`) lleno se responde igual y no se guarda. |
| GET /api/v1/public/service-requests/{publicRef} | Estado para el cliente (RECEIVED, IN_REVIEW, SCHEDULED, IN_PROGRESS, COMPLETED, NOT_ACCEPTED, CANCELLED), sucursal, equipo, preferencia y, si está confirmada, fecha y horas. Sin nombres, teléfonos, dirección ni técnico. |

### 5.11 Servicio a domicilio: interno

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/service-requests?status&branchId&customerId&search&page&size | ADMIN, BRANCH_MANAGER, RECEPTIONIST | Bandeja de su ámbito con la visita activa de cada fila; TECHNICIAN 403. |
| POST /api/v1/service-requests | Ídem + CSRF | 201; solicitud registrada por el personal con `customerId` o `registerCustomer`. |
| GET /api/v1/service-requests/{id} | Ídem | Detalle, visitas, historial, consentimientos del formulario, avisos y `actions`. |
| POST /{id}/review · PUT /{id}/customer · POST /{id}/reject · POST /{id}/cancel | Ídem + CSRF | Revisión, asociación de cliente (409 `CUSTOMER_ALREADY_LINKED`, `POSSIBLE_DUPLICATE_CUSTOMER`), rechazo y cancelación con motivo (409 `INVALID_TRANSITION`, `VISIT_ALREADY_STARTED`). |
| PUT /{id}/branch | ADMIN, BRANCH_MANAGER + CSRF | 200, o 204 si la solicitud sale del ámbito del usuario; 409 `VISIT_ACTIVE`. |
| POST /api/v1/service-requests/{id}/visits | ADMIN, BRANCH_MANAGER, RECEPTIONIST + CSRF | 201 visita PROPOSED o CONFIRMED; idempotente por `operationId`; 409 `CUSTOMER_NOT_LINKED`, `VISIT_ALREADY_ACTIVE`, `NO_SHIFT`, `OUTSIDE_WORKING_HOURS`, `DURING_BREAK`, `SCHEDULE_CONFLICT`; 400 `VISIT_IN_PAST`, `TECHNICIAN_NOT_ELIGIBLE`. |
| GET /api/v1/service-visits?from&to&branchId&technicianId | Autenticado | Agenda (máx. 42 días); el técnico solo recibe las suyas. |
| GET /api/v1/service-visits/{id} | Con acceso a la visita | Detalle de la visita. |
| GET /api/v1/service-visits/technicians?branchId | Personal | Técnicos que se pueden asignar en la sucursal. |
| GET /api/v1/service-visits/availability?technicianId&date&durationMinutes | Personal | Jornada, horas libres cada 30 min y visitas confirmadas del día. |
| POST /{id}/confirm · PUT /{id}/schedule · POST /{id}/cancel | Personal + CSRF | Confirmar (idempotente), reprogramar (hora y/o técnico, con el horario anterior en el historial) y cancelar (libera la franja). |
| POST /{id}/start · POST /{id}/complete | Técnico asignado, ADMIN, BRANCH_MANAGER + CSRF | Iniciar (409 `VISIT_NOT_TODAY`) y cerrar con resultado. |
| POST /api/v1/service-visits/{id}/repair-order | Personal + CSRF | Crea (con `operationId`) o vincula una orden del mismo cliente; 409 `REPAIR_LINK_NOT_ALLOWED`, `VISIT_ALREADY_LINKED`, `ORDER_CUSTOMER_MISMATCH`. |
| GET /api/v1/service-visits/by-repair-order/{orderId} | Con acceso a la orden | Visita de origen de una orden. |
| GET /api/v1/technician-schedules?branchId · PUT /{technicianId} | Consulta: personal; cambio: ADMIN, BRANCH_MANAGER | Semana del técnico; los días en sucursales ajenas se conservan (400 `DAY_AT_OTHER_BRANCH`). |
| GET · PUT /api/v1/branches/{id}/service-settings | Consulta: personal; cambio: ADMIN, BRANCH_MANAGER | Duración estimada y margen entre visitas. |
| GET /api/v1/portal-settings | ADMIN | Configuración completa: reglas, mensajes, `slug`, `previousSlugs`, `updatedAt`, `updatedBy`, `version`. |
| PUT /api/v1/portal-settings | ADMIN + CSRF | Reglas, mensajes y `enabled` con `version` (409 `STALE_VERSION`); 400 `DAYS_RANGE_INVALID`, `HTML_NOT_ALLOWED` (bienvenida ≤ 300, confirmación ≤ 500 caracteres). |
| PUT /api/v1/portal-settings/slug | ADMIN + CSRF | `{slug, version}`; 400 `SLUG_INVALID`, `SLUG_RESERVED`; el anterior queda como alias. |

### 5.12 Avisos y dashboard

| HTTP | Acceso | Respuesta |
|---|---|---|
| GET /api/v1/notifications?status&branchId&from&page&size | ADMIN (todas), BRANCH_MANAGER (sus sucursales) | Filas sin contenido: evento, canal, estado, motivo de omisión, referencia, destino enmascarado, intentos, último error, próximo intento. |
| GET /api/v1/notifications/{id} · POST /{id}/retry | Ídem (+ CSRF) | Detalle con intentos; reintento solo de `FAILED` (409 `NOTIFICATION_NOT_RETRYABLE`). |
| GET /api/v1/notifications/dev-inbox | ADMIN, solo en modo `inbox` | Últimos 100 mensajes de la bandeja de desarrollo (404 con transporte real). |
| GET /api/v1/dashboard?branchId | Autenticado | `{branch, consolidated, indicators:[{key, count}], upcomingVisits, generatedAt}`. Sin `branchId`: todas las sucursales del usuario (400 `BRANCH_REQUIRED` para recepción). Claves: `REPAIRS_RECEIVED`, `REPAIRS_DIAGNOSING`, `REPAIRS_READY`, `REQUESTS_PENDING`, `REQUESTS_UNDER_REVIEW`, `VISITS_TODAY`, `STOCK_OUT`, `STOCK_LOW`; técnico: `MY_REPAIRS_*`, `MY_VISITS_TODAY`. |

Estados de un aviso: pendiente, enviándose, enviado (aceptado por el proveedor), falló y no enviado (sin
consentimiento, consentimiento retirado o sin dirección).

## 6. Pantallas

Todas usan el sistema de diseño ER-DS-001 (`docs/design-system.md`) y consumen la API anterior.

- **Inicio:** bienvenida con rol y alcance, franja de prioridades, indicadores agrupados (taller, a domicilio,
  agenda, inventario) con la urgencia en texto, próximas visitas y acciones rápidas; gestión alterna entre la
  sucursal seleccionada y *Todas mis sucursales*.
- **Reparaciones:** pestañas de estado ligadas a la URL; recepción en pasos con `CustomerPicker` (combobox ARIA,
  debounce de 300 ms, respuestas antiguas descartadas) y barra de envío que indica lo que falta; ficha con avance
  del ciclo, acciones agrupadas (un solo formulario abierto a la vez), cotizaciones, *Repuestos utilizados* con
  totales, *Avisos al cliente* y comprobante imprimible.
- **Clientes:** listado con búsqueda, ficha con edición, historial de reparaciones y visitas, y *Avisos al cliente*
  por canal con origen y texto leído.
- **A domicilio:** bandeja con pestañas por estado, alta telefónica, ficha con el recorrido de la solicitud y el
  siguiente paso; *Configuración del portal* (solo ADMIN) con interruptor, enlace copiable, código QR generado en
  el navegador (PNG y SVG `electronica-rojas-solicitudes-<slug>`), reglas y mensajes.
- **Agenda:** semana en siete columnas o día, agenda vertical en pantallas estrechas, filtros por sucursal y
  técnico, visitas propuestas con borde discontinuo; *Horarios de técnicos*; *Mis visitas* para el técnico.
- **Inventario:** existencias de la sucursal, vista consolidada, movimientos y catálogo (formulario oculto hasta
  «Nuevo producto» o «Editar», con precios y existencias iniciales).
- **Transferencias**, **Notificaciones** (con bandeja de desarrollo rotulada como simulación), **Auditoría**,
  **Sucursales** y **Usuarios** en *Más*.
- **Público:** `/solicitar/<slug>` (formulario según las reglas del portal; aviso neutro si está pausado) y
  `/solicitud/<ref>` (estado). El inicio de sesión es solo para colaboradores.
- **Paletas:** azul eléctrico (predeterminada), petróleo profesional e índigo tecnológico, guardadas solo en el
  navegador.

## 7. Criterios de aceptación extremo a extremo

| ID | Escenario | Criterio verificable | Evidencia |
|---|---|---|---|
| E2E-01 | Inicialización | La aplicación arranca, Flyway aplica V1–V12 y health responde sanitizado con PostgreSQL aislado. | `ElectronicaRojasApplicationTests` |
| E2E-02 | Seguridad por sucursal | Encargado A no consulta ni muta la sucursal B; ADMIN accede a ambas. | `BranchAccessIntegrationTests`, `StockMovementIntegrationTests.foreignBranchStockIsIndistinguishableFromMissing` |
| E2E-03 | Transferencia normal | 10/2 → transferir 4 → 6/6; dos movimientos y auditoría. | `StockTransferIntegrationTests.transferMovesUnitsAndRecordsBothMovementsAndAudit` |
| E2E-04 | Existencias insuficientes | 2 disponibles, se piden 4 → rechazo y cero cambios. | `StockTransferIntegrationTests.insufficientStockRejectsTheWholeTransfer` |
| E2E-05 | Reintento | Mismo `operationId` → un registro, saldos sin duplicar. | `StockTransferIntegrationTests.replayingAnOperationReturnsTheSameResultWithoutApplyingItAgain` |
| E2E-06 | Concurrencia | Peticiones simultáneas; el saldo nunca es negativo. | `InventoryConcurrencyIntegrationTests` |
| E2E-07 | Recepción | Cliente y orden en RECEIVED con código e historial coherentes. | `RepairOrderIntegrationTests.receptionCreatesCustomerOrderHistoryAndAuditTogether` |
| E2E-08 | Reparación | Transición inválida rechazada; técnico y motivo registrados. | `RepairOrderIntegrationTests.invalidTransitionLeavesStatusAndHistoryUntouched` |
| E2E-09 | Cancelación con custodia | Una orden cerrada sin reparar sigue en custodia hasta registrar su entrega. | `RepairOrderIntegrationTests.unrepairableNeedsAReasonAndIsReturnedToTheCustomer` (`inCustody` hasta la entrega) |
| E2E-10 | Formulario público | La solicitud queda pendiente; no se crea visita. | `PublicServiceRequestIntegrationTests.validSubmissionIsReceivedPendingNotScheduled` |
| E2E-11 | Seguridad web | Mutación anónima o sin CSRF no modifica datos. | `PingControllerTest`, `AuthenticationIntegrationTests.loginWithoutCsrfTokenIsRejected` |
| E2E-12 | Portafolio | README, instrucciones reproducibles y CI. | `README.md`, `.github/workflows/ci.yml` |

## 8. Requisitos no funcionales

- **NFR-001 · MUST** · Operaciones habituales fluidas con paginación e índices; medir antes de fijar un SLA.
- **NFR-002 · MUST** · Separación de demo y piloto, secretos fuera de Git, HTTPS y restauración de respaldos probada
  antes de operar datos reales.
- **NFR-003 · MUST** · Despliegue en un servicio propio con base de datos exclusiva; nunca tablas, usuarios ni
  secretos de otros proyectos.
- **NFR-004 · MUST** · Pruebas de backend y frontend en CI; cambios pequeños y migraciones seguras.

## 9. Exclusiones y preguntas comerciales

Fuera de alcance: POS, pasarela de pagos, facturación electrónica de Costa Rica, WhatsApp real, reserva automática
de citas, rutas optimizadas y múltiples empresas. Antes de un piloto, confirmar logística, permisos reales,
categorías, costos, garantías y privacidad (ER-BR-001 §11).

## Control de cambios

Las versiones 1.0 a 1.7 se publicaron bajo el nombre anterior del proyecto (ER-ARCH-001 ADR-020), organizadas
por fases de entrega.

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0 | 25 de septiembre de 2026 | Baseline propuesto para implementación incremental. | Jefferson Rojas Brizuela |
| 1.1 | 25 de septiembre de 2026 | Contrato HTTP de identidad y sucursales. | Jefferson Rojas Brizuela |
| 1.2 | 26 de septiembre de 2026 | Contrato HTTP de inventario, transferencias y auditoría. | Jefferson Rojas Brizuela |
| 1.3 | 26 de septiembre de 2026 | Vista consolidada, `stockStatus`, detalles de auditoría, política de contraseñas, bloqueo de login; clientes y reparaciones. | Jefferson Rojas Brizuela |
| 1.4 | 27 de septiembre de 2026 | Recepción inteligente de clientes; servicio a domicilio, agenda y horarios. | Jefferson Rojas Brizuela |
| 1.5 | 27 de septiembre de 2026 | Repuestos, consentimiento por canal, outbox de avisos y dashboard. | Jefferson Rojas Brizuela |
| 1.6 | 27 de septiembre de 2026 | Rediseño visual, navegación adaptable y comprobante de recepción (FR-REP-006). | Jefferson Rojas Brizuela |
| 1.7 | 28 de septiembre de 2026 | Costo y precio, existencias iniciales, precio y cobro de repuestos, portal público configurable. | Jefferson Rojas Brizuela |
| 2.0 | 28 de septiembre de 2026 | Versión pública como Electrónica Rojas (ER-FS-001). Reorganizada por módulo en lugar de por fase; estado real por módulo y por requisito, contrastado con el código y las pruebas (FR-BRH-001, FR-DSH-001, FR-RPT-001, FR-SRV-004 y FR-NOT-001 pasan a PARTIAL; FR-NOT-002 a PENDING); se añaden `GET /service-visits/{id}` y `GET /service-visits/technicians`, que existían sin documentar; criterios E2E con la prueba que los demuestra. Sin cambios de comportamiento. | Jefferson Rojas Brizuela |
