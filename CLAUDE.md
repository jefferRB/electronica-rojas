# CLAUDE.md

Guía breve para asistentes de ingeniería que trabajen en este repositorio. El propietario del proyecto decide y
revisa cada cambio; los documentos de `docs/` mandan sobre este archivo.

## Fuentes de verdad

`docs/00_Contexto_Proyecto.md`, `docs/01_Estandares_Ingenieria.md` (ER-ENG-001), `docs/02_Reglas_Negocio.md`
(ER-BR-001), `docs/03_Especificacion_Funcional.md` (ER-FS-001), `docs/04_Arquitectura_ADR.md` (ER-ARCH-001),
`docs/05_Trazabilidad_Requisitos.md` y `docs/design-system.md` (ER-DS-001). Ante contradicciones:
ER-BR > ER-FS > ER-ARCH; detener el cambio incompatible y proponer una enmienda.

## Invariantes que no se rompen

- Autorización en el servidor por rol y sucursal (`BranchService`); fuera de alcance = 404 indistinguible.
- Existencias nunca negativas; todo cambio de existencias pasa por `StockLedger`.
- Transferencias, recepción, repuestos y visitas: atómicos e idempotentes por `operationId`.
- Máquina de estados de reparación solo en `RepairPolicy`/`RepairWorkflow`; un equipo se entrega una vez.
- Un técnico nunca tiene dos visitas confirmadas superpuestas.
- Avisos solo con consentimiento, por *outbox* en la misma transacción; nunca envío dentro de la transacción.
- Historial y auditoría append-only; sin datos personales en logs ni auditoría.
- Sesión de servidor con CSRF activo; sin JWT, sin registro público, sin credenciales fijas.
- Flyway con `ddl-auto=validate`; **nunca editar una migración existente** (V1–V12): cambios en una versión nueva.
- DTOs `record`, `ProblemDetail` sin stack traces, instantes UTC y presentación en America/Costa_Rica.

## Comandos (Windows / PowerShell, desde la raíz)

```powershell
docker info                                                          # Engine activo
docker compose up -d db                                              # PostgreSQL 17 en 127.0.0.1:5433
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 # backend con DB_* desde .env
cd backend; .\mvnw.cmd test                                          # todas las pruebas (Docker)
cd backend; .\mvnw.cmd test "-DexcludedGroups=integration"           # sin Docker
cd frontend; npm ci; npm run lint; npm test; npm run check:contrast; npm run build
```

## Reglas de trabajo

- Leer el código y los documentos antes de cambiar nada; cambios mínimos y sin tocar áreas no relacionadas.
- Reportar solo resultados reales: comandos ejecutados, pruebas aprobadas y fallidas, y lo no ejecutado.
- Nunca introducir secretos, `.env` reales ni datos personales reales; solo datos ficticios.
- No hacer commit, push, ni cambiar versiones o estructura sin que el propietario lo pida.
- Si cambia un contrato (regla, endpoint, permiso, esquema o decisión técnica), actualizar el documento
  correspondiente, su control de cambios y la trazabilidad.
