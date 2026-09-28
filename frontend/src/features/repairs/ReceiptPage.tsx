import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { formatDateTime } from '../../shared/lib/format'
import { Icon } from '../../shared/ui/Icon'
import { ErrorState, LoadingState } from '../../shared/ui/States'
import { branchKeys, fetchBranch } from '../branches/branchesApi'
import { formatPhone } from '../customers/customersApi'
import { fetchRepairOrder, repairKeys } from './repairsApi'

/**
 * Printable reception receipt of an existing order (counter roles only: the route requires
 * receiveRepairs and the order itself is read with the same authorized GET as its record). It shows
 * what the customer handed over and in which condition; never the diagnosis, quotes or internal notes.
 * The custody period and legal wording are pending business decisions (ER-BR-001 §11), so the sheet
 * does not state any policy.
 */
export function ReceiptPage() {
  const orderId = Number(useParams().orderId)
  const order = useQuery({
    queryKey: repairKeys.detail(orderId),
    queryFn: ({ signal }) => fetchRepairOrder(orderId, signal),
  })
  const branchId = order.data?.branch.id
  const branch = useQuery({
    queryKey: branchId ? branchKeys.detail(branchId) : ['branches', 'detail', 'none'],
    queryFn: ({ signal }) => fetchBranch(branchId!, signal),
    enabled: branchId !== undefined,
  })

  if (order.isPending) return <LoadingState label="Preparando comprobante…" />
  if (order.isError) return <ErrorState error={order.error} onRetry={() => order.refetch()} />
  const data = order.data

  return (
    <section className="page">
      <h1 className="visually-hidden">Comprobante de recepción {data.orderCode}</h1>
      <div className="receipt-toolbar no-print">
        <Link className="button button-ghost" to={`/repairs/${data.id}`}>
          <Icon name="chevronLeft" />
          Volver a la orden
        </Link>
        <button type="button" className="button button-primary" onClick={() => window.print()}>
          <Icon name="printer" />
          Imprimir
        </button>
      </div>

      <article className="receipt" aria-label={`Comprobante de recepción ${data.orderCode}`}>
        <header className="receipt-head">
          <div className="receipt-brand">
            <span className="brand-mark" aria-hidden="true">
              <Icon name="cpu" />
            </span>
            <div>
              <strong>Electrónica Rojas · {data.branch.name}</strong>
              <span className="small">{branch.data?.address ?? `Sucursal ${data.branch.code}`}</span>
            </div>
          </div>
          <div className="receipt-code">
            <span className="small">Comprobante de recepción</span>
            <span className="mono">{data.orderCode}</span>
            <span className="small">Recibido el {formatDateTime(data.receivedAt)}</span>
          </div>
        </header>

        <section className="receipt-section">
          <h2>Cliente</h2>
          <dl className="receipt-grid">
            <div>
              <dt>Nombre</dt>
              <dd>{data.customer.fullName}</dd>
            </div>
            <div>
              <dt>Teléfono</dt>
              <dd>{data.customer.phone ? formatPhone(data.customer.phone) : '—'}</dd>
            </div>
            <div>
              <dt>Correo</dt>
              <dd>{data.customer.email ?? '—'}</dd>
            </div>
          </dl>
        </section>

        <section className="receipt-section">
          <h2>Equipo recibido</h2>
          <dl className="receipt-grid">
            <div>
              <dt>Tipo</dt>
              <dd>{data.device.type}</dd>
            </div>
            <div>
              <dt>Marca y modelo</dt>
              <dd>
                {data.device.brand} {data.device.model ?? ''}
              </dd>
            </div>
            <div>
              <dt>Número de serie</dt>
              <dd className="mono wrap-anywhere">{data.device.serialNumber ?? 'Sin registrar'}</dd>
            </div>
          </dl>
        </section>

        <section className="receipt-section">
          <h2>Estado al recibir</h2>
          <dl className="receipt-grid">
            <div>
              <dt>Falla reportada por el cliente</dt>
              <dd className="text-block">{data.reportedFault}</dd>
            </div>
            <div>
              <dt>Condición física</dt>
              <dd className="text-block">{data.physicalCondition}</dd>
            </div>
            <div>
              <dt>Accesorios entregados</dt>
              <dd className="text-block">{data.accessories ?? 'Ninguno'}</dd>
            </div>
          </dl>
        </section>

        <div className="receipt-signatures">
          <div>Firma del cliente</div>
          <div>Recibido por {data.receivedBy.fullName}</div>
        </div>

        <p className="receipt-foot">
          Documento de control de recepción de la orden {data.orderCode}; no es una factura ni una cotización. Impreso el{' '}
          {formatDateTime(new Date().toISOString())}
        </p>
      </article>
    </section>
  )
}
