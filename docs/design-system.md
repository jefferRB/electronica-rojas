# Grafito Eléctrico — sistema de diseño de Electrónica Rojas

- **Documento:** ER-DS-001 · versión 2.0 · 28 de septiembre de 2026
- **Ámbito:** frontend React (`frontend/src`). Complementa ER-FS-001 §6 y ER-ARCH-001 ADR-018.
- **Fuente de verdad:** `frontend/src/styles/tokens.css`. Este documento explica cómo usarlos.

## 0. Identidad visual

Una herramienta de mostrador y de taller: se usa muchas horas, con prisa y a veces desde un teléfono. La
identidad busca que se lea como un instrumento técnico confiable, no como una página de marketing.

- **Navegación grafito.** Una barra flotante oscura y redondeada (`--c-nav`) separa el marco de la aplicación del
  trabajo; en móvil se convierte en barra inferior para una mano.
- **Superficies claras.** Fondo gris azulado muy claro (`--c-bg`) y superficies blancas: el contenido operativo
  (órdenes, existencias, agenda) siempre sobre blanco, con separadores finos en lugar de cajas anidadas.
- **Azul eléctrico** (`--c-primary`) para la acción principal, enlaces y foco: una sola acción destacada por grupo.
- **Petróleo** (`--c-accent`) como acento secundario para etiquetas de sección e iconos; nunca compite con la
  acción principal.
- **Jerarquía explícita:** etiqueta de sección → título → descripción → acción → indicadores → contenido.
- **Accesibilidad antes que decoración:** contraste WCAG 2.2 AA comprobado en CI, estados siempre con texto y
  objetivos táctiles de 44 px.
- **Móvil de verdad:** tablas que se vuelven tarjetas, filtros plegables y controles de tamaño táctil; no un
  escritorio reducido.
- **Iconografía propia de taller y electrónica** (iconos SVG dibujados para el proyecto, marca `cpu`).

## 1. Principios

1. **Un sistema, no pantallas sueltas.** Toda pantalla se compone con los mismos tokens y componentes.
   Si una diferencia se puede resolver con un token, no se añaden valores CSS específicos de una página.
2. **Un módulo, una superficie.** Cabecera, navegación, filtros, operaciones, contenido y paginación viven
   en una sola superficie blanca separada por líneas; nada flota sobre el fondo ni se apilan tarjetas dentro
   de tarjetas. El inicio es la excepción deliberada (composición editorial libre).
3. **Jerarquía clara:** etiqueta de sección → título → descripción breve → acción principal → indicadores
   → contenido.
4. **El color refuerza, nunca informa solo.** Cada estado lleva texto (insignia, bandera, etiqueta); el
   color, el punto, la regla lateral o el borde discontinuo lo refuerzan.
5. **Móvil primero, no escritorio reducido.** Tablas que se vuelven tarjetas, navegación inferior para una
   mano, filtros plegables, controles de 44 px y campos de 16 px.
6. **Discreción.** Sombras suaves, degradados solo en áreas destacadas (bienvenida del inicio, login),
   animaciones cortas y funcionales, sin efectos decorativos continuos.

## 2. Tokens

### 2.1 Color (paleta predeterminada «Azul eléctrico»)

| Token | Valor | Uso |
|---|---|---|
| `--c-bg` | `#F4F7FB` | Fondo general |
| `--c-surface` | `#FFFFFF` | Tarjetas, menús, campos |
| `--c-surface-2` / `--c-surface-3` | `#F8FAFD` / `#EEF2F8` | Zonas secundarias, carriles de pestañas |
| `--c-text` | `#111C2E` | Texto principal |
| `--c-text-2` | `#61718A` | Texto secundario (4.96:1 sobre blanco) |
| `--c-text-3` | `#7A889E` | Solo decorativo o marcadores de posición |
| `--c-border` / `--c-border-strong` | `#E1E8F2` / `#CFD9E6` | Separadores |
| `--c-border-control` | `#8492A6` | Borde de campos (3.16:1, WCAG 1.4.11) |
| `--c-nav` | `#19283E` | Barra flotante, barra inferior |
| `--c-primary` / `--c-primary-hover` | `#2563EB` / `#1D4ED8` | Acción principal, enlaces, foco |
| `--c-primary-soft` / `--c-primary-soft-text` | `#EAF1FE` / `#1D4ED8` | Estados activos, informativos |
| `--c-accent` | `#0F766E` | Acento petróleo (etiquetas de sección, iconos secundarios) |
| `--c-ok` · `--c-ok-text` · `--c-ok-soft` | `#15803D` · `#146C36` · `#E8F6EE` | Éxito |
| `--c-warn` · `--c-warn-text` · `--c-warn-soft` | `#B76C10` · `#92560A` · `#FDF3E3` | Advertencia (el ámbar puro solo para marcas no textuales) |
| `--c-error` · `--c-error-text` · `--c-error-soft` | `#C83446` · `#B42336` · `#FDECEE` | Error, acciones de cierre |
| `--c-nav-focus` | `#93C5FD` | Anillo de foco sobre la navegación oscura (8.2:1) |
| `--g-hero` | degradado grafito → azul → petróleo | Solo bienvenida del inicio y login |

Los valores orientativos del encargo se ajustaron solo donde el contraste lo exigía: el texto ámbar usa
`#92560A` (el `#B76C10` original da 4.07:1) y los extremos del degradado se oscurecieron para que el texto
secundario y la etiqueta superen 4.5:1.

### 2.2 Paletas

Una paleta es un valor de `data-theme` en `<html>` que sobrescribe solo los tokens de marca, navegación y
degradado; neutros y estados son comunes para que un estado se lea igual en todas.

| Paleta | `data-theme` | Primario | Acento | Navegación |
|---|---|---|---|---|
| Azul eléctrico (predeterminada) | — | `#2563EB` | `#0F766E` | `#19283E` |
| Petróleo profesional | `petroleum` | `#0F766E` | `#475569` | `#1B2A2F` |
| Índigo tecnológico | `indigo` | `#4F46E5` | `#0E7490` | `#1C1F3F` |

El selector está en el menú de la cuenta (escritorio) y en el menú lateral (móvil). La preferencia se
guarda en `localStorage` (`electronica-rojas.theme`) y se aplica antes del primer pintado (`main.tsx`); no hay
configuración en el backend ni migraciones. Un valor desconocido vuelve a la predeterminada.

### 2.3 Tipografía

- Familia: `Inter` si está instalada, luego `Segoe UI Variable` / `Segoe UI` / `system-ui` (sin descargar
  fuentes). Monoespaciada para códigos: `JetBrains Mono` → `Cascadia Mono` → `ui-monospace`.
- Escala (`--fs-*`): 11 · 12 · 14 · 16 · 17 · 20 · 24 · 30 · 36 px. Pesos 400/500/600/700.
- `h1` 30 px (24 px en móvil), `h2` 20 px, `h3` 17 px; interlineado 1.55 en texto, 1.2 en títulos.
- Códigos (`.mono`, `.order-code`): sin partirse en los guiones; en tarjetas móviles solo parten si no caben.

### 2.4 Espaciado, forma, elevación y capas

- Espaciado en ritmo de 4 px: `--sp-1` (4) … `--sp-12` (48). Márgenes laterales `--gutter` 16 px (24 px ≥ 1024).
- Radios: `--r-sm` 8 · `--r-md` 10 (controles) · `--r-lg` 14 · `--r-xl` 18 (tarjetas) · `--r-2xl` 22 (barra, bienvenida).
- Sombras: `--sh-xs`, `--sh-sm`, `--sh-card` (tarjetas y paneles: contacto fino + caída amplia y suave),
  `--sh-md` (hover, paneles de operación), `--sh-lg` (barra flotante, menús).
- Controles: `--h-control` 40 px en escritorio y **44 px** en móvil o puntero táctil; `--h-control-sm` 32/44 px.
- Capas: `--z-sticky` 50 · `--z-nav` 100 · `--z-dropdown` 200 · `--z-overlay` 300 · `--z-drawer` 310. Los menús
  se abren por encima del contenido y nunca detrás de tarjetas.
- Movimiento: 120/200/280 ms con `--ease`; `prefers-reduced-motion` las reduce a 0.01 ms.

### 2.5 Puntos de corte y anchos

| Ancho | Comportamiento |
|---|---|
| < 480 | Botones de formulario a todo el ancho, paginación en rejilla, filas de visita en una columna |
| < 768 | Móvil: tablas → tarjetas, filtros plegables, barra inferior, selector de sucursal bajo la barra, controles de 44 px |
| 768–1199 | Tableta: barra con selector y botón «Menú» (drawer) |
| < 1100 | Agenda semanal como agenda vertical; detalle en una columna |
| ≥ 1200 | Navegación completa en la barra; ≥ 1536 los enlaces muestran icono; si falta espacio, el selector de sucursal se recorta con elipsis (la navegación nunca se solapa) |

Anchos máximos: `--w-page` 88 rem (listados), `--w-form` 60 rem (`.page-form`: recepción, transferencias,
registros), `--w-narrow` 44 rem.

## 3. Estructura de archivos

```
src/styles/
  tokens.css      tokens y paletas
  base.css        reset, tipografía, foco, utilidades
  components.css  página, tarjetas, botones, campos, estados, pestañas, filtros, tablas, línea de tiempo
  controls.css    interruptor, campo con prefijo (₡, /solicitar/), fichas seleccionables, fila «agregar»
  shell.css       barra flotante, menús, drawer, barra inferior, login, páginas públicas
  module.css      superficie de módulo: cabecera, franjas, operación, secciones de ficha y ritmo por ancho
  dashboard.css · repairs.css · service.css · inventory.css   diseños de módulo
  pricing.css · portal.css   precios y totales de repuestos; configuración y página del portal público
src/shared/ui/    componentes compartidos (abajo)
```

## 4. Componentes

| Componente | Archivo | Cuándo usarlo |
|---|---|---|
| `ModuleSurface` | `shared/ui/ModuleSurface.tsx` | Superficie única de un módulo: recibe las props de `PageHeader` (su primera franja) y, como hijos, las franjas en orden de lectura |
| `PageHeader` | `shared/ui/PageHeader.tsx` | Cabecera: `breadcrumb`, `eyebrow` + `icon`, `title`, `description`, `meta` (insignias/chips), `actions`. Dentro de `ModuleSurface` salvo en el inicio |
| `SectionCard` | `shared/ui/SectionCard.tsx` | Tarjeta con encabezado uniforme (icono, título, subtítulo, acciones) |
| `Icon` | `shared/ui/Icon.tsx` | Iconos SVG propios (sin librería). Decorativos por defecto; con `label` solo si van solos |
| `Alert` | `shared/ui/Alert.tsx` | Mensajes `success` · `info` · `warn` · `error` con icono y título opcional |
| `EmptyState` · `LoadingState` · `ErrorState` | `shared/ui/States.tsx` | Vacío (qué pasa y qué hacer), carga anunciada, error traducido con «Reintentar» |
| `FilterBar` | `shared/ui/FilterBar.tsx` | Búsqueda siempre visible; filtros secundarios plegados en móvil con contador; «Limpiar filtros» (`onClear` + `canClear`) solo cuando hay algo aplicado |
| `SearchField` · `TextField` · `SelectField` · `TextAreaField` | `shared/ui/Field.tsx` | Etiqueta asociada, pista y error con `aria-describedby` |
| `MoneyField` | `shared/ui/Field.tsx` | Monto en colones con prefijo ₡; se interpreta con `parseMoney`/`parseAmount` (`shared/lib/money.ts`), la misma convención en cotizaciones, precios y costos |
| `Switch` | `shared/ui/Switch.tsx` | Ajuste encendido/apagado: checkbox nativo con `role="switch"`, etiqueta asociada, descripción y estado en texto opcional («Activo»/«Desactivado») |
| Fichas seleccionables | clases `.toggle-chips` / `.toggle-chip` | Selección múltiple corta (días, provincias): checkbox nativo con marca ✓ además del color |
| `Pagination` | `shared/ui/Pagination.tsx` | Listados paginados |
| `Avatar` | `shared/ui/Avatar.tsx` | Iniciales con color estable por nombre (siempre junto al nombre) |
| `MoreMenu` · `UserMenu` · `MobileDrawer` · `BottomNav` | `app/` | Navegación (ver §5) |
| Insignias | clases `.badge-{ok,info,warn,error,muted,accent}`, `.badge-dashed` | Estado con punto + texto; propuesta = borde discontinuo |
| Pestañas y segmentados | `.tabs`, `.tabs-line`, `.segmented` | `.tabs-line` (subrayadas): estados dentro de un panel y navegación de sección; `.segmented`: vista (semana/día, alcance). `aria-pressed`, `aria-current` o `aria-selected` según el patrón |
| Panel | `.card.panel` | Base de `ModuleSurface`: sus hijos directos son franjas de borde a borde (ver §5). Dentro de una ficha, una sección de lista con paginación (`SectionCard className="panel"`) |
| Navegación de sección | `.tabs-line.section-tabs` | Secciones de un módulo (inventario, notificaciones) como franja bajo la cabecera; el contexto (sucursal) va en `meta` con `.context-chip` |
| Resumen de ficha | `.record-summary` | Progreso (`RepairProgress`, `RequestJourney`) y «Acciones disponibles» como dos franjas bajo la cabecera |
| Secciones de documento | `.module-section`, `.module-section-tonal`, `.module-section-title`, `.module-columns`, `.module-form` | Formularios largos y fichas: secciones separadas por línea; fondo tonal solo para una subsección que lo necesite |
| Franja de operación | `.card-highlight` o `.operation-slot` (panel de `useDisclosure`) como hijo de la superficie | Un formulario abierto desde una acción se despliega dentro del módulo: fondo tonal y regla izquierda en color primario, sin tarjeta flotante |
| Progreso | `RepairProgress`, `RequestJourney` (`.progress`) | Etapas de una orden o solicitud leídas de su historial |

Botones: `.button` + `-primary` (acción principal, una por grupo), `-secondary`, `-ghost` (acción discreta
sobre superficie clara), `-danger` (cancelar, rechazar, declarar sin reparación), `-danger-solid`
(confirmación destructiva en formularios de motivo), `-small`, `-block`, `-icon`.

**Toasts:** no se usan. Los resultados de una acción se muestran como `Alert` persistente en la página
(anunciado con `role="status"`), porque un mensaje que desaparece solo no cumple bien WCAG 2.2.1 y puede
perderse en el mostrador. **Confirmaciones:** el patrón existente de panel de operación (`.card-highlight`)
con «Confirmar»/«Cancelar»; los destructivos piden motivo y confirman en rojo.

## 5. Patrones

- **Navegación.** Escritorio: Inicio, Reparaciones, A domicilio, Agenda (o Mis visitas), Clientes,
  Inventario; *Más* agrupa Transferencias, Notificaciones, Auditoría, Sucursales y Usuarios; selector de
  sucursal y menú de cuenta a la derecha. Tableta: botón «Menú» con drawer. Móvil: barra inferior con los
  cuatro módulos diarios + «Menú». Todo sale de `app/navigation.ts` según el rol (ocultar es solo UX).
- **Drawer.** Diálogo modal etiquetado, foco inicial en «Cerrar menú», Tab atrapado, Escape y fondo cierran,
  scroll bloqueado, foco devuelto al botón que lo abrió, se cierra al navegar.
- **Un formulario de operación a la vez.** Las acciones abren un panel `.card-highlight` que recibe el foco;
  las acciones de cierre van separadas y en estilo peligro.
- **Módulo: una superficie.** Toda pantalla interna (salvo el inicio) es `<section className="page">` con una
  `ModuleSurface`. Sus hijos directos son franjas de borde a borde separadas por una línea, en este orden
  cuando existen: cabecera (etiqueta, título, descripción, contexto en `meta`; acción principal en la línea
  del título, secundarias en `button-secondary` o `button-ghost`) → avisos de resultado (`Alert`) → navegación
  (`.tabs-line.section-tabs` entre secciones, `.tabs-line` de estados) → franja de operación abierta →
  encabezado de la vista activa (`.card-header`) → `FilterBar` / `.filters` / `.panel-toolbar` →
  `LoadingState` / `ErrorState` / `EmptyState` → `.table-scroll` → `Pagination` (pie tonal). Los módulos con
  secciones (inventario) ponen la superficie en el layout y cada vista devuelve solo franjas (fragmento),
  nunca otra tarjeta. Un filtro único conserva ancho de campo. Una barra de marca de 3 px (`--g-brand-mark`)
  corona la superficie; no se usa en ningún otro elemento.
- **Ritmo.** `--panel-x` es la sangría de todas las franjas: 32 px (≥ 1280), 24 px (768–1279) y 16 px en móvil,
  con radio `--r-2xl` (móvil `--r-xl`). La superficie comparte el ancho de la barra flotante (`--w-page`).
  En móvil la cabecera se apila y la acción principal ocupa el ancho.
- **Fichas.** Cabecera → avisos → `.record-summary` (progreso y acciones, dos franjas) → franja de operación →
  `.split`: secciones principales separadas por línea y columna lateral tonal (`--c-surface-2`) con borde
  izquierdo; en < 1100 px la lateral pasa debajo. Solo conservan caja propia los elementos que se benefician de
  ella (falla reportada, totales, cotizaciones).
- **Formularios largos** (recepción, solicitud telefónica, transferencia, portal, alta de producto): un
  documento dentro de la superficie, secciones `.module-section` o `.subsection` separadas por línea, pasos
  numerados como encabezado de sección y barra de envío al final (fija mientras falten datos).
- **Tablas.** En móvil cada fila es una tarjeta: la celda `.cell-title` es el título, el resto usa
  `data-label`; la celda `.cell-status` sube a la línea del título, alineada a la derecha. Dentro de un panel las filas son elementos de lista separados por una línea (sin tarjeta
  dentro de tarjeta) y los campos se ordenan en dos columnas con la etiqueta encima del valor; `.cell-title`,
  `.actions`, `.span-row`, celdas con `colspan` y formularios en línea ocupan la fila completa. Solo matrices
  que deben conservar columnas usan `.table-scroll.keep-columns` (scroll dentro de su propio contenedor,
  nunca de la página).
- **Formularios.** Una columna cuando no hay espacio (`auto-fit` con `min(100%, …)`), grupos con
  `.form-section-title`, pasos numerados (`.step-card` sobre `.module-section`) y barra de envío fija que dice
  qué falta.
- **Estados vacíos** explican por qué no hay datos y ofrecen la acción siguiente según el permiso.
- **Impresión.** `@media print` oculta la navegación; el comprobante de recepción (`/repairs/:id/receipt`)
  es una hoja limpia.

- **Formularios de configuración largos** (portal público): secciones separadas por línea (`.portal-section`)
  dentro de la superficie del módulo y una barra de guardado que solo queda fija mientras hay cambios sin
  guardar.
- **Excepción de color:** el código QR se dibuja siempre negro sobre blanco con su zona de silencio, sin tokens
  de paleta, porque los lectores de QR necesitan ese contraste.

## 6. Accesibilidad (WCAG 2.2 AA)

- **Contraste verificado:** `npm run check:contrast` resuelve cada paleta y comprueba 31 pares por paleta
  (texto 4.5:1; foco, bordes de campo y marcas 3:1; incluidos los tramos del degradado). Falla en CI si un
  par no cumple.
- **Foco visible** (`:focus-visible`, 2 px con separación; claro sobre la barra oscura) y nunca oculto bajo
  la barra flotante o la barra de envío (`scroll-padding`).
- **Objetivos táctiles** de 44 px en móvil (botones, campos, pestañas, segmentados, enlaces de tarjeta).
- **Campos a 16 px** para evitar el zoom de iOS; etiquetas visibles y asociadas; errores con icono y texto.
- **Nombre accesible con el texto visible** (2.5.3): p. ej. «Taller: Reparaciones» en la barra inferior.
- Menús con `aria-expanded`/`aria-controls`, Escape y devolución del foco; enlace «Saltar al contenido».
- Estados siempre con texto; propuestas con borde discontinuo; urgencia con bandera «Requiere acción».

## 7. Rendimiento

- División de código por rutas (`React.lazy` en `app/router.tsx`): el JavaScript inicial pasó de 564 kB
  (160 kB gzip) a 274 kB (84 kB gzip); cada módulo se descarga al abrirlo. Login, inicio y el shell siguen
  en el paquete principal.
- Sin dependencias visuales nuevas: iconos SVG propios, CSS con variables, sin framework de componentes.
- Sin fuentes web descargadas; animaciones solo de opacidad/transform.

## 8. Cómo crear una pantalla nueva

1. `<section className="page">` (o `page page-form` para formularios) + `ModuleSurface` con etiqueta e icono.
2. Listados: dentro de la superficie, pestañas `.tabs-line`, `FilterBar` (con `onClear`), `.data-table` dentro
   de `.table-scroll` (primera celda `.cell-title`, estado `.cell-status`, resto con `data-label`) y
   `Pagination`. Fichas: `.record-summary` + `.split` con `SectionCard`. Formularios: `.module-form` con
   `.module-section`. Nunca una `.card` suelta sobre el fondo.
3. `LoadingState`, `ErrorState` con reintento y `EmptyState` con acción.
4. Ninguna medida o color literal: solo tokens. Si falta un token, se añade a `tokens.css` y se documenta aquí.
5. Verificar en 320, 390, 768, 1280 y 1440 px, con teclado y `npm run check:contrast`.

## Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 1.0 | 27 de septiembre de 2026 | Sistema de diseño inicial (bajo el nombre anterior del proyecto): tokens, tres paletas, componentes, patrones, accesibilidad y responsive. | Jefferson Rojas Brizuela |
| 1.1 | 28 de septiembre de 2026 | Panel de trabajo para todos los listados (pestañas, filtros, tabla y paginación en una sola superficie), pestañas subrayadas, navegación de sección, resumen de ficha (progreso + acciones), filas de lista en dos columnas en móvil, «Limpiar filtros», token `--sh-card`, insignias con borde y estados vacíos con halo. Sin cambios de lógica, API ni permisos. | Jefferson Rojas Brizuela |
| 1.2 | 28 de septiembre de 2026 | `Switch`, `MoneyField`, fichas seleccionables, hojas `controls.css`, `pricing.css` y `portal.css`, patrón de configuración en una tarjeta y excepción de color del QR (ER-FS-001 §5.11). | Jefferson Rojas Brizuela |
| 1.3 | 28 de septiembre de 2026 | Superficie de módulo (`ModuleSurface`, `module.css`): la cabecera y la acción principal pasan dentro de una única superficie en todos los módulos internos (el inicio se conserva); franjas de operación en lugar de tarjetas flotantes; fichas con secciones planas y columna lateral tonal; formularios largos como documento; estado en la línea del título en móvil. Correcciones: solape de «Más» con el selector de sucursal a 1440–1920 px, desborde de 9 px a 320 px (selector de sucursal), casillas de consentimiento bajo su texto y altura desigual de campos de fecha. Sin cambios de API, permisos, claves de consulta ni rutas. | Jefferson Rojas Brizuela |
| 2.0 | 28 de septiembre de 2026 | Identidad propia «Grafito Eléctrico» de Electrónica Rojas (ER-DS-001, ER-ARCH-001 ADR-020): §0 describe los principios visuales directamente; se retira la referencia a una fuente externa de inspiración; clave de paleta `electronica-rojas.theme`; favicon con la marca `cpu`. Tokens, paletas, componentes y responsive sin cambios. | Jefferson Rojas Brizuela |
