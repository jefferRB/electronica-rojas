import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../shared/api/httpClient'
import { describeError } from '../../shared/api/describeError'
import { Alert } from '../../shared/ui/Alert'
import { TextField } from '../../shared/ui/Field'
import { Icon } from '../../shared/ui/Icon'
import { PasswordField } from '../../shared/ui/PasswordField'
import { useLogin, useSession } from './session'

/**
 * FR-AUTH-001. For collaborators only: there is intentionally no sign-up link (BR-BRH-003) and no
 * entry to the customer portal, which customers reach through its own link or QR (BR-SRV-009).
 */
export function LoginPage() {
  const session = useSession()
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  if (session.data) return <Navigate to={from} replace />

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    login.mutate(
      { email: email.trim(), password },
      {
        onSuccess: () => navigate(from, { replace: true }),
        onError: () => setPassword(''),
      },
    )
  }

  const errorMessage =
    login.error instanceof ApiError && login.error.status === 401
      ? 'Correo o contraseña incorrectos.'
      : login.error
        ? describeError(login.error)
        : null

  return (
    <div className="login-screen">
      <aside className="login-aside" aria-label="Sobre Electrónica Rojas">
        <div className="login-brand">
          <span className="brand-mark" aria-hidden="true">
            <Icon name="cpu" />
          </span>
          <span className="brand-text">
            <span className="brand-name">Electrónica Rojas</span>
            <span className="brand-tagline">Servicio técnico multisucursal</span>
          </span>
        </div>
        <div>
          <p className="eyebrow">
            Centro de operaciones
          </p>
          <h2>Taller, visitas a domicilio e inventario en un solo lugar.</h2>
          <p>Cada colaborador ve únicamente las sucursales y las tareas que le corresponden.</p>
          <ul className="login-features">
            <li>
              <Icon name="wrench" />
              Órdenes de reparación con historial completo
            </li>
            <li>
              <Icon name="calendar" />
              Agenda de técnicos sin reservas dobles
            </li>
            <li>
              <Icon name="transfer" />
              Existencias y transferencias entre sucursales
            </li>
          </ul>
        </div>
      </aside>

      <main className="login-main">
        <div className="login-card">
          <form className="card" onSubmit={handleSubmit} noValidate>
            <div className="login-brand">
              <span className="brand-mark" aria-hidden="true">
                <Icon name="cpu" />
              </span>
              <div>
                <h1>Iniciar sesión</h1>
                <p className="muted small">Electrónica Rojas · Inventario y reparaciones</p>
              </div>
            </div>

            {errorMessage && <Alert tone="error">{errorMessage}</Alert>}

            <TextField
              label="Correo electrónico"
              type="email"
              name="email"
              autoComplete="username"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <PasswordField
              label="Contraseña"
              name="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={setPassword}
            />

            <button
              type="submit"
              className="button button-primary button-block button-large"
              disabled={login.isPending || !email || !password}
            >
              {login.isPending ? 'Ingresando…' : 'Ingresar'}
            </button>
            <p className="login-note">Las cuentas las crea un administrador. Si no tienes acceso, contacta a tu encargado.</p>
          </form>
        </div>
      </main>
    </div>
  )
}
