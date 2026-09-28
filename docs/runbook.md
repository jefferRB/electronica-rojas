# Runbook local

Diagnóstico y operación del entorno de desarrollo (Windows + PowerShell). Complementa el *Quick start* del
`README.md` y ER-ARCH-001 §7 y §16.

## Comprobaciones rápidas

```powershell
docker info                                        # debe mostrar una sección "Server" (Engine activo)
docker compose ps                                  # db en estado "healthy"
curl.exe http://localhost:8080/actuator/health     # {"status":"UP"}
curl.exe http://localhost:8080/api/v1/ping         # {"status":"ok","timestamp":"..."}
curl.exe -i http://localhost:8080/actuator/env     # 401: todo lo demás está protegido
curl.exe -i http://localhost:8080/api/v1/auth/me   # 401 hasta iniciar sesión
```

## Síntomas frecuentes

| Síntoma | Causa / comprobación | Solución |
|---|---|---|
| `docker --version` funciona pero `docker info` falla con `open //./pipe/docker_engine` o `Docker Desktop is unable to start` | CLI instalada, **Engine** detenido | Abrir Docker Desktop y esperar *Engine running*. Log: `%LOCALAPPDATA%\Docker\log\host\monitor.log`. |
| Docker Desktop pide `WSL update required`, o `wsl --status` solo imprime la ayuda | WSL 2 sin instalar o desactualizado | En PowerShell de **administrador**: `wsl --install --no-distribution` (o `wsl --update`), reiniciar y abrir Docker Desktop. La virtualización debe estar activa en BIOS/UEFI. |
| `docker` no se reconoce en una terminal ya abierta | PATH actualizado después de instalar | Abrir una terminal nueva (o reiniciar VS Code). |
| `'url' must start with "jdbc"` (llegó el literal `${DB_URL}`) o `Failed to determine a suitable driver class` | El proceso Java no tiene las variables `DB_*` | Arrancar con `scripts\start-backend.ps1` o definir las variables (Quick start, paso 3). |
| `Connection to 127.0.0.1:5433 refused` | Contenedor detenido o no saludable | `docker compose ps`, `docker compose logs db`. |
| `password authentication failed for user "electronica_rojas"` | Se cambió la contraseña de `.env` después de crear el volumen (PostgreSQL solo la aplica en la primera inicialización) | Restaurar la contraseña original o, **solo si los datos locales son desechables**, `docker compose down -v` y `docker compose up -d db`. |
| El puerto 5433 ya está en uso | Otro servicio (por ejemplo, otra base local) | `netstat -ano \| findstr 5433`. Definir otro `POSTGRES_HOST_PORT` en `.env`; el script deriva `DB_URL` de él. No detener servicios ajenos. |
| Flyway: `Unsupported Database: PostgreSQL` | Falta `flyway-database-postgresql` | Ya está en `pom.xml`; comprobar con `.\mvnw.cmd dependency:tree "-Dincludes=org.flywaydb"`. |
| Flyway: *checksum mismatch* / *validate failed* | Se editó una migración ya aplicada (ocurrió con V6, ver ER-ARCH-001 ADR-012) | Respaldar primero (`pg_dump -Fc`), restaurar el archivo exactamente como se aplicó y llevar el cambio a una `V{n}__...sql` nueva. No usar `flyway repair` sin demostrar que el esquema real coincide. Solo con datos desechables: `docker compose down -v`. |
| Testcontainers: `Could not find a valid Docker environment` | Engine detenido | Igual que las primeras filas. No cambiar a H2 ni deshabilitar pruebas. |
| Maven lanza `NoSuchElementException` justo después de `Scanning for projects` | Maven en modo interactivo sin consola (tareas en segundo plano, algunas tareas de VS Code) | Modo batch: `.\mvnw.cmd -B spring-boot:run` (el script ya lo usa). |
| El backend se detiene con `BOOTSTRAP_ADMIN_PASSWORD must have at least 12 characters` | Contraseña de ejemplo o débil | Definir una contraseña propia en `.env`, o quitar las líneas `BOOTSTRAP_ADMIN_*` cuando ya exista un administrador. |
| Una operación de existencias responde 409 «being changed by another operation» | No se obtuvo el bloqueo (víctima de deadlock o contención) | No se aplicó nada; reintentar con el **mismo** `operationId`. |
| El login responde 429 «Too many failed login attempts» | Bloqueo temporal (5 fallos por cuenta / 20 por IP en 15 min) | Esperar 15 minutos (`Retry-After`) o reiniciar el backend local; límites en `app.security.login.*`. |
| Programar una visita responde `NO_SHIFT` u `OUTSIDE_WORKING_HOURS` | El técnico no tiene jornada ese día en la sucursal de la solicitud | Definir la semana en *Agenda → Horarios de técnicos* (ADMIN o BRANCH_MANAGER). |
| El formulario público responde 429 | Límite por dirección (5/hora) o por teléfono (3/día) | Esperar (`Retry-After`) o reiniciar el backend local; límites en `app.public.requests.*`. |
| No se ofrece «Iniciar diagnóstico» en una orden nueva | La orden no tiene técnico (solo gestión o el técnico asignado pueden iniciarla) | Asignar primero un técnico activo de esa sucursal. |
| Un repuesto no se registra: 409 «Los repuestos se registran mientras la orden está «En reparación»» | La orden no está en IN_REPAIR | Pasarla a *En reparación* (técnico asignado o gestión). |
| Un aviso muestra «No enviado: el cliente no aceptó avisos» | No hay consentimiento de correo registrado | Registrarlo en la tarjeta *Avisos al cliente* del cliente (con el texto leído); los eventos nuevos se enviarán. |
| Los avisos siguen «Pendiente de envío» | Trabajador desactivado (`NOTIFICATIONS_WORKER_ENABLED=false`) o SMTP fallando | Revisar *Notificaciones* (último error, próximo intento) y el log; los fallidos se reintentan desde ahí. |
| El login siempre responde 403 | Falta o caducó el token CSRF | La SPA lo gestiona; con curl, llamar a `GET /api/v1/auth/csrf` y enviar el valor de la cookie `XSRF-TOKEN` en `X-XSRF-TOKEN`. |
| `npm ci` falla con `EPERM ... rolldown-binding...node` | Un servidor Vite en marcha bloquea su binario nativo (Windows) | Detener `npm run dev` antes de `npm ci`, o usar `npm install`. Si `node_modules` quedó a medias, `npm install`. |
| La ejecución de un `.ps1` está bloqueada | Política de ejecución de Windows | `powershell -ExecutionPolicy Bypass -File ...` (solo para ese proceso). |

## Operaciones

| Tarea | Comando |
|---|---|
| Parar la base sin borrar datos | `docker compose stop db` |
| Recrear la base local (**borra los datos**) | `docker compose down -v; docker compose up -d --wait db` |
| Respaldo local | `docker compose exec db pg_dump -U electronica_rojas -Fc electronica_rojas > respaldo.dump` (fuera del repositorio; `*.dump` está en `.gitignore`) |
| Comprobar que ninguna migración aplicada cambió | `bash scripts/check-migrations.sh <ref-base>` (Git Bash; requiere historial Git) |
| Backend con puerto de depuración 5005 | `powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -EnableDebugger` |
| Frontend contra otro backend | `$env:BACKEND_URL = "http://localhost:8081"; npm run dev` |
| Correo real (solo con un proveedor autorizado) | En `.env`: `MAIL_MODE=smtp`, `MAIL_FROM`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Con `MAIL_MODE=smtp` y sin servidor, el backend no arranca. |

## Datos demo para el portafolio

Escenario **ficticio** de unas nueve semanas de operación en SUC-001 y SUC-002 (catálogo, existencias, movimientos,
transferencias, clientes, reparaciones, cotizaciones, repuestos, solicitudes, visitas, avisos y auditoría) para
capturas, video y entrevistas. **Solo desarrollo:** nunca en producción ni contra una base compartida (ER-ARCH-001
ADR-021).

- **Qué hace:** reproduce las operaciones con los casos de uso reales (validación, permisos, `StockLedger`,
  historial, auditoría y outbox) como las harían Jefferson Rojas, Susan Rojas, Maicol Armas, Julián Alvarez y
  Pedro Fernandez, con fechas hasta hoy y visitas para los próximos días. No crea sucursales ni usuarios: los
  busca por código, nombre y rol, y omite la carga (con un aviso en el log) si falta alguno.
- **Requisitos:** base local (`localhost`), `MAIL_MODE=inbox` y ninguna operación previa en la base (sin
  productos, clientes, órdenes, solicitudes ni horarios). Nunca borra ni modifica datos existentes.
- **Datos ficticios:** nombres inventados, teléfonos `0555-01xx` (fuera de la numeración en uso en Costa Rica),
  correos `@example.test` y direcciones generales marcadas como «referencia ficticia». Ningún aviso sale de la
  máquina: van a la bandeja de desarrollo.

| Tarea | Comando |
|---|---|
| Activar y cargar (una vez, al arrancar) | `powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DemoSeed` |
| Arranques siguientes | Sin `-DemoSeed`. Con él no pasa nada: si el escenario ya está, el cargador no hace nada. |
| Respaldar antes de cargar | `docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > ../respaldo-antes-demo.dump` (desde Git Bash: PowerShell 5.1 corrompe la salida binaria) |
| Resetear (volver a la base sin demo) | Detener el backend y restaurar el respaldo: `docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < ../respaldo-antes-demo.dump` (Git Bash). Sin respaldo, solo con datos desechables: `docker compose down -v`, `docker compose up -d --wait db`, crear sucursales y colaboradores y cargar de nuevo. |

Las operaciones «de hoy» dependen de la hora de la carga (lo que aún no ocurrió queda pendiente); cárguelo en horario
diurno. La bandeja de desarrollo vive en memoria: tras reiniciar el backend queda vacía, aunque *Notificaciones*
conserva el estado de cada aviso.

## Advertencias

- `docker compose config` sin `--quiet` imprime la contraseña interpolada: evitarlo al compartir salidas.
- Un volumen Docker **no** es un respaldo; un respaldo solo cuenta si se restauró con éxito en una base separada.
- Nunca guardar la contraseña de la base en `.vscode/launch.json` ni en ningún archivo versionado.
