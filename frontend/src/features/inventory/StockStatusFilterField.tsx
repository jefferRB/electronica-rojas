import { SelectField } from '../../shared/ui/Field'
import type { StockStatusFilter } from './inventoryApi'

const OPTIONS: { value: StockStatusFilter | ''; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'ATTENTION', label: 'Requieren atención' },
  { value: 'OUT_OF_STOCK', label: 'Sin existencias' },
  { value: 'LOW', label: 'Bajo mínimo' },
]

export function StockStatusFilterField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <SelectField label="Estado de existencias" value={value} onChange={(event) => onChange(event.target.value)}>
      {OPTIONS.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </SelectField>
  )
}
