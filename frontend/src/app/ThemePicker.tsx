import { useId, useState } from 'react'
import { readTheme, saveTheme, THEMES, type ThemeId } from '../shared/ui/theme'

/** Palette choice (radio group): applied at once and remembered in this browser only. */
export function ThemePicker() {
  const [theme, setTheme] = useState<ThemeId>(readTheme)
  const name = useId()
  return (
    <fieldset className="theme-options">
      <legend className="menu-heading">Paleta de colores</legend>
      {THEMES.map((option) => (
        <label key={option.id} className="theme-option">
          <input
            type="radio"
            name={name}
            value={option.id}
            checked={theme === option.id}
            onChange={() => {
              setTheme(option.id)
              saveTheme(option.id)
            }}
          />
          <span className="theme-swatch" aria-hidden="true">
            {option.swatch.map((color) => (
              <span key={color} style={{ background: color }} />
            ))}
          </span>
          {option.label}
        </label>
      ))}
    </fieldset>
  )
}
