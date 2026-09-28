# Reglas de negocio

*Sucursales, inventario, reparaciones, servicio a domicilio y avisos al cliente*

- **Documento:** ER-BR-001
- **Versión / fecha:** 2.0 · 28 de septiembre de 2026
- **Responsable:** Jefferson Rojas Brizuela
- **Estado general:** reglas **propuestas**; ninguna validada todavía por los propietarios del negocio.

> Documento vivo. Toda modificación de una regla lleva ID, versión, fecha, justificación y los cambios
> correlativos en ER-FS-001 (y en ER-ARCH-001 si afecta entidades, concurrencia o despliegue).

## 0. Estados de una regla

Cada regla lleva dos estados independientes. **Implementado no significa validado.**

| Dimensión | Valor | Significado |
|---|---|---|
| Validación | `PROPOSED` | Propuesta por el responsable del proyecto a partir del escenario; sin confirmación del negocio. |
| | `VALIDATED` | Confirmada por los propietarios de la electrónica, con fecha y evidencia anotadas en el control de cambios. |
| Implementación | `IMPLEMENTED` | Existe código y pruebas automatizadas que la demuestran (ver `05_Trazabilidad_Requisitos.md`). |
| | `PARTIAL` | Implementada en parte; la nota indica qué falta. |
| | `NOT IMPLEMENTED` | Documentada, sin implementar. |

**Situación actual:** 0 reglas `VALIDATED`. Todas son `PROPOSED`.

## 1. Alcance y supuestos de negocio

Una única empresa de venta de productos y repuestos y de reparación de electrodomésticos, con dos sucursales y
posibilidad de añadir más sin cambiar el modelo. La administración interna y el portal público de solicitudes
a domicilio forman parte del producto. El proyecto de portafolio no implica adopción por la empresa.

- SUPUESTO A VALIDAR: la empresa vende productos y repuestos además de reparar electrodomésticos.
- SUPUESTO A VALIDAR: cada sucursal tiene inventario físico y al menos un colaborador responsable.
- SUPUESTO A VALIDAR: los clientes pueden pedir una fecha de visita, pero el personal confirma la disponibilidad.
- Fuera de alcance: facturación electrónica, caja/POS, compras, contabilidad, nómina, multiempresa y WhatsApp real.

## 2. Conceptos y límites de propiedad

| Concepto | Definición |
|---|---|
| Branch | Sucursal física con código, nombre, dirección y estado. |
| Product | Artículo del catálogo global que la empresa posee para venta o uso como repuesto. |
| BranchStock | Existencias por producto y sucursal; jamás equivale a un equipo del cliente. |
| StockMovement | Evidencia inmutable de entrada, ajuste, salida, transferencia o uso en reparación, con actor y referencia. |
| Customer | Persona que entrega un equipo o solicita una visita; distinta del colaborador (User). |
| RepairOrder | Registro de custodia, diagnóstico y reparación de un equipo ajeno. |
| ServiceRequest | Solicitud de servicio a domicilio; no es una cita. |
| ServiceVisit | Visita programada por la empresa (técnico, fecha y hora); única fuente de la hora. |
| UserBranch | Autorización explícita de un colaborador a una sucursal; nunca se deduce de lo que envía el navegador. |

## 3. Sucursales y colaboradores

- **BR-BRH-001 · MUST** · La empresa tiene N sucursales con código único. Una sucursal desactivada conserva su
  historial y no recibe nuevas operaciones. · `PROPOSED` · `IMPLEMENTED`
- **BR-BRH-002 · MUST** · Un usuario puede pertenecer a una o varias sucursales. ADMIN accede a todas;
  encargado y recepción solo a las asignadas; el técnico solo a órdenes y visitas asignadas, con los datos mínimos
  necesarios. · `PROPOSED` · `IMPLEMENTED`
- **BR-BRH-003 · MUST** · Sin autorregistro de empleados. Solo ADMIN crea, habilita o deshabilita cuentas y
  asignaciones. · `PROPOSED` · `IMPLEMENTED`
- **BR-BRH-004 · MUST** · No puede desactivarse ni degradarse la última cuenta administradora activa; una cuenta
  deshabilitada, con rol o contraseña cambiados pierde su sesión en la siguiente petición. · `PROPOSED` ·
  `IMPLEMENTED`

| Rol | Permisos propuestos |
|---|---|
| ADMIN | Sucursales, usuarios, catálogo, inventario global, reparaciones, portal público, avisos y auditoría. |
| BRANCH_MANAGER | Operación y existencias de sus sucursales; técnicos, órdenes, horarios y avisos de su ámbito; sin seguridad global. |
| RECEPTIONIST | Clientes, recepción y entrega de equipos, decisión de cotizaciones, solicitudes y visitas de sus sucursales; inventario de consulta. |
| TECHNICIAN | Órdenes y visitas asignadas, diagnóstico, estados técnicos y repuestos de sus órdenes; sin acceso general al inventario. |

## 4. Catálogo y existencias

- **BR-INV-001 · MUST** · Un producto tiene SKU normalizado único, nombre, categoría, unidad (unidades), estado
  activo y mínimo configurable por sucursal. · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-002 · MUST** · Para cada par (producto, sucursal) existe a lo sumo un registro de existencias. Las
  existencias son enteros no negativos. · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-003 · MUST** · Entradas, salidas y ajustes requieren un usuario autorizado y dejan un movimiento con
  cantidad, tipo, motivo, instante UTC, sucursal y operación de origen. · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-004 · MUST** · Un producto desactivado no admite movimientos ordinarios nuevos pero se consulta en el
  histórico. No se borran productos con existencias o movimientos. · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-005 · MUST** · La alerta de bajo stock se deriva de la cantidad y del mínimo, no de un contador
  aparte. Estados, iguales en todas las vistas: **Sin existencias** (cantidad 0); **Bajo mínimo** (cantidad
  positiva y menor que un mínimo > 0); **Existencias normales** en otro caso. Un producto en 0 nunca se muestra
  como «bajo mínimo». · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-006 · MUST** · Las existencias representan unidades físicas de la empresa. Los equipos de clientes
  pertenecen exclusivamente al módulo de reparaciones. · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-007 · MUST** · Costo y precio son conceptos distintos: **costo unitario** (lo pagado por la empresa,
  uso interno) y **precio de venta sugerido**. Ambos en colones con dos decimales, opcionales (vacío =
  desconocido) y nunca negativos. El costo solo lo reciben ADMIN y BRANCH_MANAGER: el servidor lo omite para los
  demás roles. Los repuestos tienen además **cobrar por defecto**. Cambiar precio, costo o ese valor afecta solo
  operaciones posteriores y queda auditado con el antes y el después. · `PROPOSED` · `IMPLEMENTED`
- **BR-INV-008 · MUST** · El producto no tiene cantidad propia (catálogo global, existencias por sucursal). Al
  crearlo se puede registrar la cantidad que ya hay en **una** sucursal activa, como entrada (`RECEIPT`) con el
  mismo mecanismo de movimientos y el motivo «Existencias iniciales al crear el producto». Producto, movimiento y
  auditoría se confirman juntos; cantidad 0 no genera movimiento. · `PROPOSED` · `IMPLEMENTED`

## 5. Transferencias y consistencia

- **BR-TRF-001 · MUST** · Origen y destino son sucursales activas distintas; producto activo y cantidad
  positiva; el actor debe poder operar origen y destino. · `PROPOSED` · `IMPLEMENTED`
- **BR-TRF-002 · MUST** · Una transferencia inmediata no permite saldo negativo. Descuento, aumento y ambos
  movimientos se confirman o revierten juntos. · `PROPOSED` · `IMPLEMENTED`
- **BR-TRF-003 · MUST** · Cada transferencia lleva un `operationId` único: repetirla con los mismos datos devuelve
  el resultado previo sin duplicar movimientos; el mismo id con otros datos se rechaza. · `PROPOSED` ·
  `IMPLEMENTED`
- **BR-TRF-004 · MUST** · Dos transferencias simultáneas no pueden consumir más de lo disponible; el mecanismo se
  verifica con PostgreSQL real. · `PROPOSED` · `IMPLEMENTED`
- **BR-TRF-005 · MUST** · El historial no se reescribe: una corrección es una operación compensatoria autorizada
  y auditada. · `PROPOSED` · `IMPLEMENTED`
- **BR-TRF-006 · SHOULD** · Transferencias con despacho y recepción (mercancía en tránsito), separadas de la
  transferencia inmediata. · `PROPOSED` · `NOT IMPLEMENTED`

| Caso | Respuesta esperada |
|---|---|
| Origen 10, destino 2, transferir 4 | Origen 6, destino 6; movimientos −4/+4 y auditoría en una transacción. |
| Origen 2, solicitar 4 | Rechazo de negocio; ambos saldos sin cambios. |
| Mismo `operationId`, doble clic | Se aplica una sola vez; respuesta estable. |
| Dos peticiones compiten por la última unidad | Solo prosperan las que cubre la disponibilidad confirmada. |
| Actor asignado solo a una sucursal ajena | Rechazo indistinguible de «no existe»; nunca se alteran existencias. |

## 6. Clientes y recepción de equipos

- **BR-CUS-001 · MUST** · Un cliente tiene nombre y al menos un medio de contacto utilizable (ver supuesto
  BR-CUS-A1: el teléfono es obligatorio). · `PROPOSED` · `IMPLEMENTED`
- **BR-CUS-002 · MUST** · Teléfonos normalizados antes de comparar. Una coincidencia sugerida nunca fusiona
  clientes sin confirmación humana. · `PROPOSED` · `IMPLEMENTED`
- **BR-CUS-003 · MUST** · Ámbito: ADMIN ve todos; BRANCH_MANAGER y RECEPTIONIST ven los clientes registrados en
  sus sucursales o con alguna orden en ellas; TECHNICIAN no tiene módulo de clientes y en sus órdenes solo ve el
  nombre. La búsqueda por teléfono exacto abarca toda la empresa pero devuelve solo id y nombre; un cliente de
  otra sucursal se usa en una recepción únicamente si quien recibe aporta el mismo teléfono registrado. ·
  `PROPOSED` · `IMPLEMENTED`
- **BR-CUS-004 · MUST** · Los datos personales de clientes no se escriben en logs técnicos ni en la auditoría; la
  auditoría guarda identificadores, códigos y sucursal. · `PROPOSED` · `IMPLEMENTED`
- **BR-CUS-005 · MUST** · Resolución de identidad al recibir: desde 3 letras o 3 dígitos el sistema sugiere
  clientes (sin distinguir mayúsculas, acentos ni orden de las palabras; número completo o fragmento). Las
  búsquedas parciales solo muestran clientes del ámbito; solo un teléfono completo reconoce a un cliente de otra
  sucursal, con id y nombre. Al guardar, el servidor vuelve a comprobar coincidencias por teléfono y por nombre
  normalizado y pide confirmación explícita antes de crear otra persona. El teléfono no es identificador único. ·
  `PROPOSED` · `IMPLEMENTED`
- **BR-CUS-006 · MUST** · Consentimiento de avisos por canal (correo, WhatsApp): declaración explícita con fecha,
  origen (sucursal, teléfono, escrito o formulario público), versión del texto y colaborador. Registrar un
  teléfono o un correo **nunca** es consentir. Las declaraciones no se modifican ni se borran: la vigente es la
  más reciente. Aceptar avisos por correo exige un correo registrado; retirar el consentimiento detiene los avisos
  pendientes de ese canal. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-001 · MUST** · Una orden identifica cliente, sucursal receptora, tipo de equipo, marca/modelo/serie si
  existen, accesorios, falla reportada, condición visual, instante UTC y responsable de recepción. · `PROPOSED` ·
  `IMPLEMENTED`
- **BR-REP-002 · MUST** · Código de orden único y legible. Fotografías opcionales, fuera de exposición pública y
  con límites de tamaño/tipo. · `PROPOSED` · `PARTIAL` (código implementado; fotografías pendientes)
- **BR-REP-008 · MUST** · Custodia: el equipo del cliente no es inventario; recibirlo no crea productos ni
  movimientos. Varias órdenes pueden pertenecer al mismo cliente, cada una con su sucursal. La recepción
  (cliente nuevo o existente, orden, primer historial y auditoría) es atómica e idempotente por `operationId`. ·
  `PROPOSED` · `IMPLEMENTED`

## 7. Ciclo de vida de reparación

| Desde | Hacia permitidos | Condición |
|---|---|---|
| RECEIVED | DIAGNOSING, CANCELLED | Inicio de revisión o cancelación con motivo. |
| DIAGNOSING | AWAITING_APPROVAL, IN_REPAIR, UNREPAIRABLE, CANCELLED | Diagnóstico; con cotización, esperar la decisión del cliente; UNREPAIRABLE con motivo. |
| AWAITING_APPROVAL | APPROVED, CANCELLED | Solo por la decisión registrada sobre la cotización. |
| APPROVED | IN_REPAIR, CANCELLED | Técnico asignado; cancelación justificada antes de iniciar. |
| IN_REPAIR | READY_FOR_PICKUP, AWAITING_APPROVAL, UNREPAIRABLE | Trabajo terminado, nueva cotización o reparación imposible. |
| READY_FOR_PICKUP | DELIVERED | Entrega con fecha y colaborador. |
| CANCELLED | DELIVERED | Cancelar el trabajo no prueba que el cliente retiró el equipo: la devolución se registra como DELIVERED. |
| UNREPAIRABLE | DELIVERED | Equipo sin reparación posible (motivo obligatorio), pendiente de devolver. |
| DELIVERED | — (terminal) | Se registra una sola vez. |

La orden conserva un **resultado** (REPAIRED, CANCELLED, UNREPAIRABLE) que no se pierde al entregar.

- **BR-REP-003 · MUST** · Historial de cada cambio de estado con autor, instante y motivo. No se eliminan órdenes
  con recepción o historial de custodia. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-004 · MUST** · El técnico solo edita diagnóstico, trabajo y estados de órdenes asignadas; recepción
  gestiona recepción y entrega y no altera diagnósticos. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-005 · MUST** · Transición fuera de la tabla prohibida. `deliveredAt`/`deliveredBy` se llenan solo al
  pasar a DELIVERED (desde READY_FOR_PICKUP, CANCELLED o UNREPAIRABLE); «en custodia» equivale a `deliveredAt`
  vacío. Una orden no se entrega dos veces, ni con solicitudes simultáneas. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-006 · MUST** · READY_FOR_PICKUP significa equipo listo, no aviso entregado. Un fallo de correo no
  cambia el estado ni revierte el trabajo. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-007 · MUST** · El consumo de repuestos registra un movimiento `OUT_FOR_REPAIR` vinculado a la orden y
  descuenta existencias de la sucursal autorizada en la misma transacción. · `PROPOSED` · `IMPLEMENTED` (mediante
  BR-REP-011..013)
- **BR-REP-009 · MUST** · Permisos por transición: DIAGNOSING, IN_REPAIR, READY_FOR_PICKUP y UNREPAIRABLE los
  realiza ADMIN, BRANCH_MANAGER o el técnico asignado; CANCELLED y DELIVERED, ADMIN, BRANCH_MANAGER o
  RECEPTIONIST. CANCELLED y UNREPAIRABLE exigen motivo; DIAGNOSING e IN_REPAIR exigen técnico asignado activo de la
  sucursal. El historial es inmutable (la base rechaza UPDATE/DELETE) y un intento rechazado no deja rastro. ·
  `PROPOSED` · `IMPLEMENTED`
- **BR-REP-010 · MUST** · Cotización: monto en colones mayor que cero con dos decimales, descripción y a lo sumo
  una pendiente por orden. Solo se emite en DIAGNOSING o IN_REPAIR y lleva la orden a AWAITING_APPROVAL; ese
  estado y APPROVED solo se alcanzan por cotización. La decisión es explícita (aprobada o rechazada), con fecha,
  actor y medio. Aprobada → APPROVED; rechazada → CANCELLED. Una cotización decidida es definitiva. ·
  `PROPOSED` · `IMPLEMENTED`
- **BR-REP-011 · MUST** · Repuestos utilizados: líneas de productos `SPARE_PART` activos con sucursal, cantidad
  entera positiva, colaborador e instante. El consumo descuenta de la sucursal **de la orden** con un movimiento
  `OUT_FOR_REPAIR`; línea, movimiento y auditoría se confirman juntos. Sin saldo negativo. Lo registran ADMIN,
  BRANCH_MANAGER o el técnico asignado mientras la orden está `IN_REPAIR`; el técnico ve existencias solo a través
  de su orden. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-012 · MUST** · Corrección de un consumo: devolución total o parcial con motivo, que repone la sucursal
  con `RETURN_FROM_REPAIR`. La línea original conserva lo consumido y acumula lo devuelto; nunca se devuelve más
  de lo que sigue en uso. Con la orden cerrada solo corrigen ADMIN o BRANCH_MANAGER. No se borran ni editan
  movimientos. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-013 · MUST** · Cada consumo y devolución lleva un `operationId`: repetirlo devuelve el resultado original
  sin volver a descontar; con otros datos es conflicto. Consumos simultáneos de las últimas unidades solo
  prosperan hasta agotar las existencias. · `PROPOSED` · `IMPLEMENTED`
- **BR-REP-014 · MUST** · Cada línea guarda una **instantánea** de precio por unidad, costo por unidad y si se
  **cobra**; cambiar el catálogo no cambia órdenes registradas. Una línea **sin cargo** descuenta inventario y
  conserva su precio, pero no suma al subtotal (no se representa con precio 0). Otro precio u otro cobro que el
  del catálogo lo decide ADMIN o BRANCH_MANAGER y queda auditado. El **subtotal cobrable** (precio × unidades en
  uso de las líneas cobradas) se muestra **aparte** de la cotización y nunca se le suma. Sin impuestos,
  facturación ni pagos. · `PROPOSED` · `IMPLEMENTED`

## 8. Servicios a domicilio

La **solicitud** (lo que pidió el cliente) y la **visita** (lo que programó la empresa) son entidades distintas.
La hora solo existe en la visita: no hay dos fuentes de verdad y reprogramar no pierde historial.

- **BR-SRV-001 · MUST** · El formulario público pide contacto, tipo de equipo, descripción del problema, dirección y
  ventana preferida. La solicitud no es una cita confirmada. · `PROPOSED` · `IMPLEMENTED`
- **BR-SRV-002 · MUST** · Estados. **Solicitud:** PENDING → UNDER_REVIEW → ACCEPTED, o REJECTED / CANCELLED (con
  motivo); una solicitud ACCEPTED vuelve a UNDER_REVIEW si su visita confirmada se cancela. **Visita:** PROPOSED →
  CONFIRMED → IN_PROGRESS → COMPLETED, o CANCELLED (con motivo) antes de empezar. A lo sumo una visita activa por
  solicitud; reprogramar conserva la anterior en el historial. · `PROPOSED` · `IMPLEMENTED`
- **BR-SRV-003 · MUST** · Una visita confirmada registra técnico, fecha y rango horario acordados, comprobando
  conflictos de agenda. · `PROPOSED` · `IMPLEMENTED`
- **BR-SRV-004 · MUST** · El formulario público aplica límites de tamaño, validación, protección antispam y de
  frecuencia, y consentimiento de contacto. · `PROPOSED` · `IMPLEMENTED` (el texto de privacidad sigue pendiente de
  revisión legal, BR-DAT-001)
- **BR-SRV-005 · MUST** · Horario explícito por técnico: a lo sumo una jornada por día de la semana, en una
  sucursal, con un descanso opcional (hora de Costa Rica). Cada sucursal define la duración estimada de una visita
  y el margen entre visitas (por defecto 90 y 30 minutos). Cambiar horarios no mueve visitas confirmadas. ·
  `PROPOSED` · `IMPLEMENTED`
- **BR-SRV-006 · MUST** · Una visita cabe completa en la jornada del técnico en la sucursal de la solicitud, fuera
  del descanso y en el futuro, y ocupa al técnico hasta su fin más el margen. Solo CONFIRMED e IN_PROGRESS
  bloquean: dos de ellas del mismo técnico nunca se superponen, ni con confirmaciones simultáneas. Una PROPOSED es
  tentativa y se revalida al confirmarla. CANCELLED y COMPLETED liberan la franja. · `PROPOSED` · `IMPLEMENTED`
- **BR-SRV-007 · MUST** · Una solicitud pública nunca se asocia sola a un cliente: el personal resuelve la
  identidad (BR-CUS-005) antes de programar. El formulario no revela si un teléfono o correo ya existe. Cambiar la
  sucursal es exclusivo de ADMIN y BRANCH_MANAGER. Cada decisión queda en un historial inmutable. · `PROPOSED` ·
  `IMPLEMENTED`
- **BR-SRV-008 · MUST** · El técnico ve solo sus visitas: dirección y teléfono únicamente mientras la visita está
  confirmada o en curso. Registra inicio (no antes del día de la visita) y resultado: resuelto, requiere taller o
  no resuelto. Si requiere taller, se crea o vincula una orden del **mismo** cliente en la misma transacción. ·
  `PROPOSED` · `IMPLEMENTED`
- **BR-SRV-009 · MUST** · Un portal público de la empresa con dirección estable `/solicitar/<slug>`, compartible por
  enlace o código QR. Solo se guarda el slug (minúsculas, números y guiones simples, 3 a 60 caracteres, sin
  palabras reservadas del sistema). Solo ADMIN lo configura. Pausarlo no cambia la dirección ni borra
  solicitudes: el servidor rechaza envíos nuevos. Los slugs anteriores siguen llevando al portal. Reglas
  configurables: fechas permitidas, días de atención, horario, provincias y tipos de equipo, y dos mensajes en
  texto simple. Nada de esto convierte la solicitud en cita. El QR no es un secreto. · `PROPOSED` · `IMPLEMENTED`

## 9. Notificaciones y consentimiento

- **BR-NOT-001 · MUST** · Aviso de equipo listo o visita confirmada solo con consentimiento válido para ese canal.
  Se guarda estado de envío e intentos; invocar al proveedor no equivale a entrega. · `PROPOSED` · `IMPLEMENTED`
- **BR-NOT-002 · MUST** · El aviso se guarda en una bandeja de salida (*outbox*) en la misma transacción del
  negocio y un proceso posterior lo entrega: nunca sale si la operación no se confirmó, y un fallo de correo nunca
  la revierte. Los reintentos no crean duplicados. · `PROPOSED` · `IMPLEMENTED`
- **BR-NOT-003 · SHOULD** · WhatsApp con plantillas aprobadas como integración futura. · `PROPOSED` ·
  `NOT IMPLEMENTED` (el consentimiento de WhatsApp ya se registra)
- **BR-NOT-004 · MUST** · Avisos: equipo listo para retirar, visita confirmada, reprogramada y cancelada.
  Reprogramar o cancelar una visita solo propuesta no se avisa. Sin consentimiento, el aviso queda como «no
  enviado» para que el personal contacte por otra vía. · `PROPOSED` · `IMPLEMENTED`
- **BR-NOT-005 · MUST** · Contenido mínimo: nombre de pila, código de orden o solicitud, equipo, sucursal y fechas
  en hora de Costa Rica. Nunca diagnósticos, notas internas, motivos del personal, precios, técnico ni enlaces de
  administración. · `PROPOSED` · `IMPLEMENTED`
- **BR-NOT-006 · MUST** · Consentimiento y dirección se revisan otra vez justo antes de enviar; los fallos
  temporales se reintentan un número acotado de veces. La garantía es «al menos una vez» hacia el proveedor y
  «enviado» significa aceptado por el proveedor. Sin proveedor autorizado, los correos van a una bandeja de
  desarrollo. · `PROPOSED` · `IMPLEMENTED` (sin proveedor real conectado)

## 10. Seguridad funcional, auditoría y datos

- **BR-SEC-001 · MUST** · Permisos de cada acción en el servidor y ámbito de lectura filtrado por sucursal. Los ids
  enviados por el cliente se comprueban contra las autorizaciones vigentes. · `PROPOSED` · `IMPLEMENTED`
- **BR-SEC-002 · MUST** · Toda operación crítica registra actor, instante UTC, sucursal, referencia y motivo. La
  auditoría no es editable. · `PROPOSED` · `IMPLEMENTED`
- **BR-DAT-001 · MUST** · Datos ficticios separados de datos reales. Antes de un piloto: retención, copias de
  seguridad, exportación, privacidad y eliminación legítima de datos personales. · `PROPOSED` · `PARTIAL` (solo
  datos ficticios; políticas previas al piloto sin definir)
- **BR-DAT-002 · MUST** · Horarios y fechas de negocio en America/Costa_Rica; instantes técnicos en UTC. No se
  infiere disponibilidad por la preferencia del cliente. · `PROPOSED` · `IMPLEMENTED`

## 11. Decisiones pendientes y supuestos aplicados

### 11.0 Preguntas abiertas para los propietarios

- ¿Qué categorías de productos y repuestos se comercializan y cuáles requieren número de serie?
- ¿Existe stock comprometido por pedidos o solo existencias físicas?
- ¿Se traslada mercancía sin recepción física en destino? ¿Quién aprueba?
- ¿Qué incluye el comprobante de recepción y cuál es el plazo de custodia?
- ¿Quién aprueba cotizaciones? ¿Cuál es la política de diagnósticos y garantías?
- ¿Qué zonas se atienden a domicilio, cómo se cobra la visita y qué horario tiene cada técnico?
- ¿Qué mensajes, canales, consentimientos y retención de datos autorizan los propietarios?

Hasta responderlas, las reglas propuestas se usan solo en la demostración.

Los supuestos siguientes permitieron implementar sin inventar políticas; todos siguen **pendientes de validación**.

### 11.1 Inventario

- **BR-INV-A1:** cada producto es `MERCHANDISE` (venta) o `SPARE_PART` (repuesto).
- **BR-INV-A2:** categoría como texto libre normalizado, sin lista cerrada.
- **BR-INV-A3:** los ajustes son `ADJUSTMENT_IN`/`ADJUSTMENT_OUT` con cantidad positiva y motivo; no se fija el
  saldo absoluto.
- **BR-INV-A4:** entradas, salidas y ajustes exigen motivo; en transferencias es opcional.
- **BR-INV-A5:** la idempotencia por `operationId` también aplica a entradas, salidas y ajustes.
- **BR-INV-A6:** TECHNICIAN no accede al módulo de inventario; ve repuestos solo a través de su orden (BR-REP-011).
- **BR-INV-A7:** solo ADMIN mantiene el catálogo global; BRANCH_MANAGER opera existencias, mínimos, movimientos y
  transferencias de sus sucursales.
- **BR-INV-A8:** el costo lo ven ADMIN y BRANCH_MANAGER; recepción ve el precio de venta; el técnico no ve costos.
- **BR-INV-A9:** las existencias iniciales se registran como `RECEIPT`, no con un tipo de movimiento nuevo.

### 11.2 Clientes y reparaciones

- **BR-CUS-A1:** el teléfono es obligatorio (identifica al cliente en el mostrador); el correo es opcional.
  Números de Costa Rica (8 dígitos, con o sin +506) e internacionales con prefijo, guardados en E.164.
- **BR-CUS-A2:** dos clientes pueden compartir teléfono (familia, empresa) solo tras confirmación explícita.
- **BR-REP-A1:** código de orden `OR-AAAA-NNNNNN` (año en hora de Costa Rica y consecutivo global, sin reinicio
  anual).
- **BR-REP-A3:** las fotografías de BR-REP-002 quedan para una fase posterior.
- **BR-REP-A4:** los repuestos se consumen solo de la sucursal de la orden; si faltan, gestión los transfiere
  antes.
- **BR-REP-A5:** el consumo se registra en `IN_REPAIR`; un repuesto olvidado tras cerrar la orden se registra como
  salida (`ISSUE`) con motivo, a cargo de gestión.
- **BR-REP-A7:** el comprobante de recepción impreso contiene solo lo que el cliente entregó y en qué estado; no
  incluye diagnóstico, cotizaciones ni notas internas, ni declara plazos, garantías o condiciones.
- **BR-REP-A8:** máximo 1 000 unidades por línea de repuesto; sin facturación.
- **BR-REP-A9:** las líneas registradas antes de existir precios quedan sin precio y cobrables («precio por
  definir»); no se inventa un precio. Ajustar el cobro de una línea ya registrada queda pendiente (hoy: devolución
  y nuevo registro).

### 11.3 Servicio a domicilio

- **BR-SRV-A1:** el cliente elige en el formulario la sucursal más cercana; el personal puede cambiarla. Solo el
  personal de esa sucursal (y ADMIN) ve la solicitud.
- **BR-SRV-A2:** provincia obligatoria (7), cantón obligatorio y distrito opcional; sin validación geográfica.
- **BR-SRV-A3:** límites del formulario: 5 solicitudes por dirección IP por hora y 3 por teléfono por día.
- **BR-SRV-A4:** el consentimiento para ser contactado sobre la solicitud es obligatorio; el de recibir avisos es
  opcional y por canal.
- **BR-SRV-A5:** recepción también programa y confirma visitas; los horarios los configuran ADMIN y
  BRANCH_MANAGER.
- **BR-SRV-A6:** valores iniciales del portal: activo, slug `servicio-a-domicilio`, todos los días, las 7
  provincias, fecha preferida hasta 90 días y tipo de equipo libre.
- **BR-SRV-A7:** con el portal pausado se muestra un mensaje neutro fijo (no configurable).

### 11.4 Avisos y dashboard

- **BR-NOT-A1:** el único canal entregado es el correo; el consentimiento de WhatsApp se registra para el futuro.
- **BR-NOT-A2:** texto de consentimiento versión `AVISOS-2026-09`, redacción propuesta pendiente de aprobación y de
  revisión legal.
- **BR-NOT-A3:** las casillas por canal del formulario público se aplican cuando el personal asocia la solicitud y
  el correo (o teléfono) coincide con el registrado.
- **BR-NOT-A4:** reintentos automáticos hasta 5 intentos (esperas de 1, 5, 15 y 60 minutos); después, reintento
  manual por ADMIN o BRANCH_MANAGER.
- **BR-NOT-A5:** cambiar el correo de un cliente conserva su consentimiento de correo (decisión abierta: podría
  exigirse reconfirmación).
- **BR-DSH-A1:** indicadores del inicio: reparaciones recibidas, en diagnóstico y listas; solicitudes nuevas y en
  revisión; visitas de hoy y de los próximos 7 días; productos sin existencias o bajo mínimo. Recepción ve una
  sucursal; gestión puede consolidar; el técnico ve su propio trabajo.

## Control de cambios

Las versiones 1.0 a 1.6 se publicaron bajo el nombre anterior del proyecto (ER-ARCH-001 ADR-020).

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0 | 25 de septiembre de 2026 | Baseline propuesto para implementación incremental. | Jefferson Rojas Brizuela |
| 1.1 | 26 de septiembre de 2026 | Supuestos de inventario (BR-INV-A1..A7). | Jefferson Rojas Brizuela |
| 1.2 | 26 de septiembre de 2026 | Estados de existencias (BR-INV-005); ámbito y datos personales de clientes (BR-CUS-003/004); custodia, permisos por transición y cotizaciones (BR-REP-008..010); estado UNREPAIRABLE y entrega de equipos cancelados o no reparables. | Jefferson Rojas Brizuela |
| 1.3 | 27 de septiembre de 2026 | Resolución de identidad (BR-CUS-005); solicitud y visita separadas (BR-SRV-002); horarios, disponibilidad, asociación de clientes y trabajo del técnico (BR-SRV-005..008). | Jefferson Rojas Brizuela |
| 1.4 | 27 de septiembre de 2026 | Repuestos en reparaciones (BR-REP-011..013); consentimiento por canal (BR-CUS-006); outbox y avisos (BR-NOT-002, BR-NOT-004..006). | Jefferson Rojas Brizuela |
| 1.5 | 27 de septiembre de 2026 | Contenido del comprobante de recepción (BR-REP-A7). | Jefferson Rojas Brizuela |
| 1.6 | 28 de septiembre de 2026 | Costo y precio (BR-INV-007), existencias iniciales (BR-INV-008), precio instantáneo de repuestos (BR-REP-014) y portal público (BR-SRV-009). | Jefferson Rojas Brizuela |
| 2.0 | 28 de septiembre de 2026 | Versión pública como Electrónica Rojas (ER-BR-001). Estados de validación e implementación por regla (§0); enmiendas integradas en el texto de cada regla; supuestos agrupados por tema y retirados los ya sustituidos (BR-REP-A2 por BR-CUS-006; BR-REP-A6 por BR-REP-A8). Sin cambios de comportamiento. | Jefferson Rojas Brizuela |
