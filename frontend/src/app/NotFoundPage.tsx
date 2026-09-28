import { Link } from 'react-router'
import { EmptyState } from '../shared/ui/States'

export function NotFoundPage() {
  return (
    <section className="page">
      <div className="card">
        <EmptyState
          icon="search"
          title="Página no encontrada"
          action={
            <Link className="button button-primary" to="/">
              Volver al inicio
            </Link>
          }
        >
          La dirección no existe o cambió. Usa el menú para ir al módulo que buscas.
        </EmptyState>
      </div>
    </section>
  )
}
