import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { describeError, fieldErrorsOf } from '../../shared/api/describeError'
import { useDebouncedValue } from '../../shared/lib/useDebouncedValue'
import { Alert } from '../../shared/ui/Alert'
import { FilterBar } from '../../shared/ui/FilterBar'
import { Icon } from '../../shared/ui/Icon'
import { EmptyState, ErrorState, LoadingState } from '../../shared/ui/States'
import { MoneyField, SearchField, SelectField, TextField } from '../../shared/ui/Field'
import { Pagination } from '../../shared/ui/Pagination'
import { Switch } from '../../shared/ui/Switch'
import { formatColones } from '../../shared/lib/format'
import { MONEY_HINT, moneyToInput, parseMoney } from '../../shared/lib/money'
import { permissions } from '../auth/permissions'
import { useSession } from '../auth/session'
import { useSelectedBranch } from '../branches/selectedBranch'
import {
  createProduct,
  fetchCategories,
  fetchProducts,
  inventoryKeys,
  updateProduct,
  type Product,
  type ProductKind,
  type StatusFilter,
} from './inventoryApi'
import { KIND_LABELS } from '../../shared/i18n/labels'

const PAGE_SIZE = 20

/** Company-wide catalog (BR-INV-001). Everyone with inventory access reads it; ADMIN maintains it. */
export function CatalogPage() {
  const { data: session } = useSession()
  const [params, setParams] = useSearchParams()
  const [searchText, setSearchText] = useState(params.get('q') ?? '')
  const search = useDebouncedValue(searchText.trim())
  // The product form is hidden until "Nuevo producto" or "Editar" opens it (one form at a time).
  const [form, setForm] = useState<{ product: Product | null } | null>(null)
  const filters = {
    search,
    category: params.get('category') ?? '',
    kind: (params.get('kind') ?? '') as ProductKind | '',
    status: (params.get('status') ?? 'ACTIVE') as StatusFilter,
    page: Number(params.get('page') ?? 0),
    size: PAGE_SIZE,
  }
  const products = useQuery({
    queryKey: inventoryKeys.products(filters),
    queryFn: ({ signal }) => fetchProducts(filters, signal),
    placeholderData: keepPreviousData,
  })
  const categories = useQuery({ queryKey: inventoryKeys.categories, queryFn: ({ signal }) => fetchCategories(signal) })

  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setParams(next, { replace: true })
  }

  if (!session) return null
  const canManage = permissions.manageCatalog(session.user.role)
  // Cost is internal: the API only sends it to these roles; the column follows the same rule.
  const showCost = permissions.viewCosts(session.user.role)

  return (
    <>
      {canManage && form && (
        <ProductForm key={form.product?.id ?? 'new'} product={form.product} categories={categories.data ?? []} onDone={() => setForm(null)} />
      )}
      <div className="card-header">
        <div className="card-heading">
          <span className="card-heading-icon tone-muted" aria-hidden="true">
            <Icon name="tag" size={18} />
          </span>
          <div>
            <h2>Catálogo</h2>
            <p className="card-subtitle">
              Productos de venta y repuestos de toda la empresa. Los precios aplican a operaciones nuevas.
            </p>
          </div>
        </div>
        {canManage && !form && (
          <button type="button" className="button button-primary" onClick={() => setForm({ product: null })}>
            <Icon name="plus" />
            Nuevo producto
          </button>
        )}
      </div>
      <FilterBar
        activeCount={[filters.category, filters.kind, params.get('status')].filter(Boolean).length}
        canClear={Boolean(searchText || filters.category || filters.kind || params.get('status'))}
        onClear={() => {
          setSearchText('')
          setParams(new URLSearchParams(), { replace: true })
        }}
        search={
          <SearchField
            label="Buscar"
            placeholder="SKU o nombre"
            value={searchText}
            onChange={(event) => {
              setSearchText(event.target.value)
              setFilter('q', event.target.value.trim())
            }}
          />
        }
      >
        <SelectField label="Categoría" value={filters.category} onChange={(event) => setFilter('category', event.target.value)}>
          <option value="">Todas</option>
          {categories.data?.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </SelectField>
        <SelectField label="Tipo" value={filters.kind} onChange={(event) => setFilter('kind', event.target.value)}>
          <option value="">Todos</option>
          <option value="MERCHANDISE">{KIND_LABELS.MERCHANDISE}</option>
          <option value="SPARE_PART">{KIND_LABELS.SPARE_PART}</option>
        </SelectField>
        <SelectField
          label="Estado"
          value={filters.status}
          onChange={(event) => setFilter('status', event.target.value === 'ACTIVE' ? '' : event.target.value)}
        >
          <option value="ACTIVE">Activos</option>
          <option value="INACTIVE">Inactivos</option>
          <option value="ALL">Todos</option>
        </SelectField>
      </FilterBar>
      {products.isPending && <LoadingState label="Cargando catálogo…" />}
      {products.isError && <ErrorState error={products.error} onRetry={() => products.refetch()} />}
      {products.data?.content.length === 0 && (
        <EmptyState icon="tag" title="Ningún producto coincide">
          Revisa los filtros; los productos inactivos se ven con Estado «Inactivos» o «Todos».
        </EmptyState>
      )}
      {products.data && products.data.content.length > 0 && (
        <>
          <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Producto</th>
                <th scope="col">Categoría</th>
                <th scope="col">Tipo</th>
                <th scope="col" className="numeric">
                  Precio venta
                </th>
                {showCost && (
                  <th scope="col" className="numeric">
                    Costo
                  </th>
                )}
                <th scope="col">Estado</th>
                <th scope="col">
                  <span className="visually-hidden">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {products.data.content.map((product) => (
                <tr key={product.id}>
                  <td className="cell-title">
                    <span className="product-cell">
                      <Link className="product-name" to={`/inventory/products/${product.id}`}>
                        {product.name}
                      </Link>
                      <span className="mono cell-sub">{product.sku}</span>
                    </span>
                  </td>
                  <td data-label="Categoría">{product.category}</td>
                  <td data-label="Tipo">
                    {KIND_LABELS[product.kind]}
                    {product.kind === 'SPARE_PART' && !product.chargeableByDefault && (
                      <span className="cell-sub">Sin cargo por defecto</span>
                    )}
                  </td>
                  <td data-label="Precio venta" className="numeric">
                    {product.salePrice === null ? <span className="muted">Sin definir</span> : formatColones(product.salePrice)}
                  </td>
                  {showCost && (
                    <td data-label="Costo" className="numeric">
                      {product.unitCost === null || product.unitCost === undefined ? (
                        <span className="muted">Sin definir</span>
                      ) : (
                        formatColones(product.unitCost)
                      )}
                    </td>
                  )}
                  <td data-label="Estado">
                    <span className={`badge ${product.active ? 'badge-ok' : 'badge-muted'}`}>
                      {product.active ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td className="actions">
                    {canManage && (
                      <button type="button" className="button button-secondary button-small" onClick={() => setForm({ product })}>
                        Editar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          <Pagination
            page={products.data.page}
            totalPages={products.data.totalPages}
            totalElements={products.data.totalElements}
            noun="productos"
            label="Paginación del catálogo"
            onChange={(page) => setFilter('page', String(page))}
          />
        </>
      )}
    </>
  )
}

function ProductForm({ product, categories, onDone }: { product: Product | null; categories: string[]; onDone: () => void }) {
  const queryClient = useQueryClient()
  const { branches, selected } = useSelectedBranch()
  const [sku, setSku] = useState(product?.sku ?? '')
  const [name, setName] = useState(product?.name ?? '')
  const [category, setCategory] = useState(product?.category ?? '')
  const [description, setDescription] = useState(product?.description ?? '')
  const [kind, setKind] = useState<ProductKind>(product?.kind ?? 'MERCHANDISE')
  const [active, setActive] = useState(product?.active ?? true)
  const [unitCost, setUnitCost] = useState(moneyToInput(product?.unitCost))
  const [salePrice, setSalePrice] = useState(moneyToInput(product?.salePrice))
  const [chargeableByDefault, setChargeableByDefault] = useState(product?.chargeableByDefault ?? true)
  const [withStock, setWithStock] = useState(false)
  const [stockBranch, setStockBranch] = useState(String(selected?.id ?? branches[0]?.id ?? ''))
  const [stockQuantity, setStockQuantity] = useState('')
  const [created, setCreated] = useState<string | null>(null)

  const cost = parseMoney(unitCost)
  const price = parseMoney(salePrice)
  const quantityValid = /^\d{1,7}$/.test(stockQuantity.trim()) && Number(stockQuantity) <= 1_000_000
  const stockReady = !withStock || (stockBranch !== '' && quantityValid)
  const margin = cost.ok && price.ok && cost.value !== null && price.value !== null ? price.value - cost.value : null
  const stockBranchName = branches.find((branch) => String(branch.id) === stockBranch)?.name

  const save = useMutation({
    mutationFn: () => {
      const data = {
        name: name.trim(),
        category: category.trim(),
        description: description.trim(),
        kind,
        unitCost: cost.ok ? cost.value : null,
        salePrice: price.ok ? price.value : null,
        // Only spare parts are used in repairs; other products keep the default.
        chargeableByDefault: kind === 'SPARE_PART' ? chargeableByDefault : true,
      }
      return product
        ? updateProduct(product.id, { ...data, active, version: product.version })
        : createProduct({
            ...data,
            sku: sku.trim(),
            initialStock: withStock ? { branchId: Number(stockBranch), quantity: Number(stockQuantity) } : undefined,
          })
    },
    onSuccess: async (saved) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['products'] }),
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
        queryClient.invalidateQueries({ queryKey: ['movements'] }),
      ])
      if (product) {
        onDone()
        return
      }
      const units = withStock ? Number(stockQuantity) : 0
      setCreated(
        units > 0
          ? `Producto ${saved.sku} creado con ${units} ${units === 1 ? 'unidad' : 'unidades'} en ${stockBranchName ?? 'la sucursal'}.`
          : `Producto ${saved.sku} creado.`,
      )
      setSku('')
      setName('')
      setDescription('')
      setUnitCost('')
      setSalePrice('')
      setStockQuantity('')
    },
  })
  const errors = fieldErrorsOf(save.error)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCreated(null)
    save.mutate()
  }

  const ready = name.trim() && category.trim() && (product || sku.trim()) && cost.ok && price.ok && stockReady && !save.isPending

  return (
    <form className="card card-highlight product-form" onSubmit={handleSubmit} noValidate aria-label={product ? `Editar ${product.sku}` : 'Nuevo producto'}>
      <h2>{product ? `Editar ${product.sku}` : 'Nuevo producto'}</h2>
      {save.isError && <Alert tone="error">{describeError(save.error)}</Alert>}
      {created && <Alert tone="success">{created}</Alert>}

      <section aria-labelledby="product-info-title">
        <p className="form-section-title" id="product-info-title">
          Información del producto
        </p>
        <div className="form-grid">
          {!product && (
            <TextField
              label="SKU"
              hint="Único. Se guarda en mayúsculas y no se puede cambiar."
              maxLength={40}
              value={sku}
              error={errors.sku}
              onChange={(event) => setSku(event.target.value)}
            />
          )}
          <TextField label="Nombre" maxLength={160} value={name} error={errors.name} onChange={(event) => setName(event.target.value)} />
          <TextField
            label="Categoría"
            maxLength={80}
            list="product-categories"
            value={category}
            error={errors.category}
            onChange={(event) => setCategory(event.target.value)}
          />
          <datalist id="product-categories">
            {categories.map((value) => (
              <option key={value} value={value} />
            ))}
          </datalist>
          <SelectField label="Tipo" value={kind} error={errors.kind} onChange={(event) => setKind(event.target.value as ProductKind)}>
            <option value="MERCHANDISE">{KIND_LABELS.MERCHANDISE}</option>
            <option value="SPARE_PART">{KIND_LABELS.SPARE_PART}</option>
          </SelectField>
          <div className="span-all">
            <TextField
              label="Descripción (opcional)"
              maxLength={1000}
              value={description}
              error={errors.description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          {product && (
            <label className="checkbox">
              <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
              Producto activo
            </label>
          )}
        </div>
        <p className="field-hint">Unidad de medida: unidades (MVP).</p>
      </section>

      <section className="subsection" aria-labelledby="product-prices-title">
        <p className="form-section-title" id="product-prices-title">
          Precios
        </p>
        <div className="form-grid">
          <MoneyField
            label="Costo unitario (opcional)"
            placeholder="0,00"
            value={unitCost}
            hint="Lo que pagaste por una unidad. Uso interno: no lo ven recepción ni técnicos."
            error={!cost.ok ? MONEY_HINT : errors.unitCost}
            onChange={(event) => setUnitCost(event.target.value)}
          />
          <MoneyField
            label="Precio de venta sugerido (opcional)"
            placeholder="0,00"
            value={salePrice}
            hint="Precio sugerido al cobrar este producto o repuesto."
            error={!price.ok ? MONEY_HINT : errors.salePrice}
            onChange={(event) => setSalePrice(event.target.value)}
          />
        </div>
        {margin !== null && (
          <p className="field-hint pricing-margin">
            Margen por unidad: <strong>{formatColones(margin)}</strong>
            {cost.ok && cost.value ? ` (${Math.round((margin / cost.value) * 100)} % sobre el costo)` : ''}
          </p>
        )}
        {kind === 'SPARE_PART' && (
          <label className="checkbox">
            <input type="checkbox" checked={chargeableByDefault} onChange={(event) => setChargeableByDefault(event.target.checked)} />
            Cobrar al cliente por defecto al utilizarlo en una reparación
            <span className="field-hint block">Es solo el valor inicial: en cada reparación se puede decidir otra cosa.</span>
          </label>
        )}
        {product && (
          <Alert tone="info">
            Un cambio de precio o costo aplica a operaciones nuevas. Las reparaciones ya registradas conservan el precio con el
            que se usó el repuesto.
          </Alert>
        )}
      </section>

      {!product && (
        <section className="subsection" aria-labelledby="product-stock-title">
          <p className="form-section-title" id="product-stock-title">
            Inventario inicial
          </p>
          <Switch
            label="Registrar existencias iniciales"
            checked={withStock}
            description="El catálogo es de toda la empresa; las existencias son de cada sucursal. Se registran como una entrada de inventario junto con el producto."
            onChange={setWithStock}
          />
          {withStock && (
            <div className="form-grid">
              <SelectField
                label="Sucursal"
                value={stockBranch}
                error={errors['initialStock.branchId']}
                onChange={(event) => setStockBranch(event.target.value)}
              >
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </SelectField>
              <TextField
                label="Cantidad en existencia"
                type="number"
                inputMode="numeric"
                min={0}
                max={1000000}
                value={stockQuantity}
                hint={
                  quantityValid && stockBranchName
                    ? Number(stockQuantity) === 0
                      ? 'Con 0 no se registra ningún movimiento.'
                      : `Entrada de ${stockQuantity} en ${stockBranchName}.`
                    : 'Unidades enteras, 0 o más.'
                }
                error={stockQuantity !== '' && !quantityValid ? 'Escribe un número entero de 0 o más.' : errors['initialStock.quantity']}
                onChange={(event) => setStockQuantity(event.target.value)}
              />
            </div>
          )}
        </section>
      )}

      <div className="form-actions">
        <button type="submit" className="button button-primary" disabled={!ready}>
          {save.isPending ? 'Guardando…' : product ? 'Guardar cambios' : 'Crear producto'}
        </button>
        <button type="button" className="button button-secondary" disabled={save.isPending} onClick={onDone}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
