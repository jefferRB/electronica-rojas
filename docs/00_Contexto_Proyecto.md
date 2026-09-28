# Contexto del proyecto

- **Proyecto:** Electrónica Rojas · plataforma de operación multisucursal
- **Versión / fecha:** 1.0 · 28 de septiembre de 2026
- **Responsable:** Jefferson Rojas Brizuela
- **Naturaleza:** proyecto de portafolio (Java full stack) construido sobre un escenario de negocio realista, con
  datos ficticios. No está en producción ni se ha ejecutado un piloto.

## 1. Problema operativo

Una electrónica con dos o más sucursales hace tres trabajos que comparten personas, clientes y existencias:

1. **Vende productos y repuestos** desde el inventario físico de cada sucursal y mueve mercancía entre ellas.
2. **Repara equipos de clientes** que se reciben en el mostrador (televisores, línea blanca, electrónica de
   consumo): el equipo queda en custodia, pasa por diagnóstico, cotización, reparación y entrega.
3. **Atiende visitas a domicilio**: el cliente pide una visita, el personal la revisa, asigna un técnico y
   confirma una hora.

Sin un sistema compartido, cada una de esas tareas se lleva por separado (hojas de cálculo por sucursal,
cuadernos, mensajes). El resultado típico, que el proyecto toma como problema a resolver, es:

- existencias que no coinciden entre sucursales y transferencias sin rastro;
- el mismo cliente registrado varias veces con datos distintos;
- equipos de clientes en el taller cuyo estado nadie conoce con certeza, incluidos los cancelados que
  siguen en custodia;
- visitas prometidas a un mismo técnico a la misma hora;
- avisos al cliente que dependen de que alguien se acuerde de llamar.

La frecuencia y el impacto de estos problemas **no se han medido**; son la hipótesis de trabajo del proyecto.

## 2. Por qué inventario, reparaciones y visitas van juntos

No son tres sistemas independientes porque sus reglas se cruzan:

| Cruce | Consecuencia en el diseño |
|---|---|
| Un repuesto usado en una reparación sale del inventario de la sucursal de la orden. | El consumo de repuestos pasa por el mismo mecanismo de bloqueo que las transferencias (`StockLedger`); nunca deja saldo negativo. |
| El equipo del cliente **no** es inventario. | Recibir un equipo crea una orden de custodia, no productos ni movimientos de existencias. |
| Una visita a domicilio puede terminar en «requiere taller». | La visita crea o vincula una orden de reparación del mismo cliente, en la misma transacción. |
| El mismo cliente aparece en el mostrador y en el formulario público. | Una única ficha de cliente con resolución de identidad asistida y sin fusiones automáticas. |
| Cada colaborador trabaja en sucursales concretas. | La autorización por sucursal es transversal y se aplica en el servidor en todos los módulos. |

## 3. Qué proviene del escenario real y qué es supuesto

### 3.1 Punto de partida

El escenario se inspira en una electrónica de dos sucursales del entorno del autor, que vende productos y
repuestos, repara electrodomésticos y atiende a domicilio, y con la que existe la posibilidad de un piloto.
«Electrónica Rojas» es el nombre del proyecto de portafolio. Las sucursales, personas, clientes, equipos y
cifras que aparecen en el código y las pruebas son **ficticios**.

### 3.2 Supuestos pendientes de validación

Las reglas de negocio (`02_Reglas_Negocio.md`) distinguen explícitamente entre regla **propuesta** e
**implementada**. Hasta hoy **ninguna regla está validada por los propietarios del negocio**. Los supuestos más
relevantes, detallados en ER-BR-001 §11, son:

- el catálogo de categorías, qué productos requieren número de serie y si existe stock comprometido;
- quién aprueba transferencias y si se necesita recepción física en destino (hoy la transferencia es inmediata);
- el contenido del comprobante de recepción, el plazo de custodia, la política de garantías y de diagnósticos;
- quién aprueba cotizaciones y cómo se cobra una visita;
- zonas atendidas, horarios de cada técnico, duración de una visita y margen de traslado (hoy 90 y 30 minutos);
- mensajes, canales y texto de consentimiento para avisar al cliente (versión `AVISOS-2026-09`, redacción
  propuesta, sin revisión legal);
- retención, copia de seguridad, exportación y eliminación de datos personales.

Mientras no se validen, el sistema se presenta como demostración con reglas propuestas y **no** como configurado
para la operación real.

## 4. Alcance del portafolio

**Incluido e implementado** (el detalle y la evidencia están en `03_Especificacion_Funcional.md` y
`05_Trazabilidad_Requisitos.md`):

- identidad, roles, sesiones con CSRF y autorización por sucursal;
- catálogo, existencias por sucursal, movimientos, transferencias atómicas e idempotentes y vista consolidada;
- clientes con resolución de identidad, órdenes de reparación con máquina de estados, cotizaciones,
  repuestos utilizados con precio instantáneo y comprobante de recepción imprimible;
- solicitudes a domicilio desde un portal público configurable, visitas, agenda sin reservas dobles y flujo del
  técnico;
- consentimiento por canal y avisos por correo mediante *transactional outbox* (bandeja de desarrollo; sin
  proveedor real);
- auditoría funcional, dashboard operativo y una SPA React responsiva y accesible.

**Fuera de alcance:** facturación electrónica, caja/POS, pagos, compras, contabilidad, nómina, WhatsApp real,
multiempresa, fotografías de equipos, mapas y cálculo de traslados.

## 5. Qué no se afirma

- **No** está en producción ni desplegado en Internet.
- **No** tiene clientes, usuarios ni datos reales.
- **No** se ha realizado un piloto con la electrónica, ni completo ni parcial.
- **No** hay proveedor de correo conectado: los avisos quedan en una bandeja de desarrollo.
- **No** hay revisión legal de los textos de consentimiento ni de la política de privacidad.
- «Implementado» significa que existe código y pruebas automatizadas que lo demuestran; **no** significa que el
  negocio haya aprobado la regla.

## Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0 | 28 de septiembre de 2026 | Documento inicial: problema, relación entre módulos, escenario frente a supuestos, alcance y límites de las afirmaciones. | Jefferson Rojas Brizuela |
