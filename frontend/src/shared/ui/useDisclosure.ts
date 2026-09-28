import { useEffect, useId, useRef, useState } from 'react'

/**
 * Show/hide an inline panel (A.5) with the ARIA disclosure pattern: the trigger exposes
 * `aria-expanded` and `aria-controls`; opening moves focus to the first field of the panel and
 * closing returns it to the trigger, so keyboard users never lose their place.
 * The panel is rendered only while open, so cancelling discards what was typed. Inside a module
 * surface the panel (`.operation-slot`) unfolds as one of its bands.
 */
export function useDisclosure() {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const wasOpen = useRef(false)

  useEffect(() => {
    if (open) {
      panelRef.current?.querySelector<HTMLElement>('input:not([type=hidden]), select, textarea')?.focus()
    } else if (wasOpen.current) {
      triggerRef.current?.focus()
    }
    wasOpen.current = open
  }, [open])

  return {
    open,
    show: () => setOpen(true),
    hide: () => setOpen(false),
    triggerProps: {
      ref: triggerRef,
      'aria-expanded': open,
      'aria-controls': panelId,
      onClick: () => setOpen((current) => !current),
    },
    panelProps: { ref: panelRef, id: panelId, className: 'operation-slot' },
  }
}
