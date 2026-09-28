/**
 * Icon set: 24×24 stroke icons drawn for this project (no icon library, so the bundle only
 * carries what the UI uses). Icons are decorative by default (aria-hidden); every important action
 * keeps a visible text label next to its icon.
 */
const PATHS = {
  home: 'M3 10.5 12 3l9 7.5M5 9.5V20h5v-6h4v6h5V9.5',
  wrench:
    'M14.7 6.3a4 4 0 0 0-5.4 5.1L3.5 17.2a1.8 1.8 0 0 0 2.6 2.6l5.8-5.8a4 4 0 0 0 5.1-5.4l-2.6 2.6-2.3-.5-.5-2.3z',
  van: 'M2.5 6.5h11v9h-11zM13.5 9.5h4l3 3.2v2.8h-7M6.5 18.5a1.8 1.8 0 1 0 0-.1M17 18.5a1.8 1.8 0 1 0 0-.1',
  calendar: 'M4 5.5h16v15H4zM4 10h16M8.5 3v4M15.5 3v4',
  users:
    'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5M16 4.3a3.5 3.5 0 0 1 0 6.4M18 14.8c1.9.7 3.2 2.6 3.5 5.2',
  box: 'M12 2.8 20.5 7v10L12 21.2 3.5 17V7zM3.5 7 12 11.2 20.5 7M12 11.2v10',
  transfer: 'M4 8h13.5M14 4.5 17.5 8 14 11.5M20 16H6.5M10 12.5 6.5 16l3.5 3.5',
  bell: 'M6 16.5V11a6 6 0 1 1 12 0v5.5l1.5 2H4.5zM10 20.5a2.2 2.2 0 0 0 4 0',
  audit: 'M6 3.5h9l3.5 3.5v13.5H6zM14.5 3.5V7.5h4M9 12h6M9 15.5h6M9 8.5h2.5',
  building: 'M4 20.5V5.5l8-3v18M12 8.5l8 3v9M2.5 20.5h19M7 8.5h1.5M7 12h1.5M7 15.5h1.5M15.5 14h1.5M15.5 17h1.5',
  userCog:
    'M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM3 20c.6-3.4 3.3-5.5 7-5.5 1 0 1.9.2 2.7.5M18 15.2a1.3 1.3 0 1 0 0 .1M18 12.5v1.2M18 17.8V19M20.6 14l-1 .6M16.4 16.4l-1 .6M20.6 17l-1-.6M16.4 14.6l-1-.6',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  chevronDown: 'm6 9 6 6 6-6',
  chevronRight: 'm9 6 6 6-6 6',
  chevronLeft: 'm15 6-6 6 6 6',
  menu: 'M4 7h16M4 12h16M4 17h16',
  close: 'M6 6l12 12M18 6 6 18',
  search: 'M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM15.5 15.5 20 20',
  plus: 'M12 5v14M5 12h14',
  logout: 'M9.5 20.5H5.5a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1h4M15.5 16.5 20 12l-4.5-4.5M20 12H9.5',
  alert: 'M12 3.5 21.5 20h-19zM12 10v4.5M12 17.5h.01',
  checkCircle: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12.3l2.7 2.7L16.2 9.5',
  info: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 11v5.5M12 7.8h.01',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3.2 2',
  printer: 'M7 8.5V3.5h10v5M7 17.5H4.5v-7a2 2 0 0 1 2-2h11a2 2 0 0 1 2 2v7H17M7 14h10v6.5H7z',
  phone:
    'M5 3.5h3.5l1.8 4.5-2.3 1.4a11 11 0 0 0 6.6 6.6l1.4-2.3 4.5 1.8V19a1.5 1.5 0 0 1-1.6 1.5A16.5 16.5 0 0 1 3.5 5.1 1.5 1.5 0 0 1 5 3.5z',
  mail: 'M3.5 5.5h17v13h-17zM3.5 6.5 12 13l8.5-6.5',
  mapPin: 'M12 21s-6.5-5.9-6.5-11a6.5 6.5 0 0 1 13 0c0 5.1-6.5 11-6.5 11zM12 12.3a2.3 2.3 0 1 0 0-4.6 2.3 2.3 0 0 0 0 4.6z',
  device: 'M3.5 4.5h17v11h-17zM8 20h8M12 15.5V20',
  cpu: 'M7 7h10v10H7zM10 10h4v4h-4zM10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4',
  filter: 'M4 5h16l-6.2 7.5V19l-3.6-1.8v-4.7z',
  user: 'M12 11.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20.5c.8-3.8 3.8-6 7.5-6s6.7 2.2 7.5 6',
  inbox: 'M3.5 13.5 6 5h12l2.5 8.5v6h-17zM3.5 13.5h5l1.5 2.5h4l1.5-2.5h5',
  package: 'M12 2.8 20.5 7v10L12 21.2 3.5 17V7zM7.8 4.9l8.5 4.3M3.5 7 12 11.2 20.5 7M12 11.2v10',
  stockOut: 'M3.5 7 12 2.8 20.5 7v10L12 21.2 3.5 17zM9 10l6 6M15 10l-6 6',
  trendDown: 'M3.5 7.5 9.5 13.5l4-4 7 7M20.5 11.5v5h-5',
  clipboard: 'M8.5 4.5h-3v16h13v-16h-3M8.5 3h7v3h-7zM8.5 11h7M8.5 14.5h7M8.5 18h4',
  check: 'm5 12.5 4.5 4.5L19 7.5',
  arrowRight: 'M4.5 12h15M13.5 6l6 6-6 6',
  palette:
    'M12 21a9 9 0 1 1 9-9c0 2.5-2 3.5-3.8 3.5h-2.4a1.8 1.8 0 0 0-1.3 3.1c.8.8.3 2.4-1.5 2.4zM7.5 11h.01M10 7.5h.01M14.5 7.5h.01M17 11h.01',
  spark: 'M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6',
  history: 'M3.5 12a8.5 8.5 0 1 0 2.5-6M3.5 4v4.5H8M12 7.5V12l3 2',
  tag: 'M3.5 12.3V4.5a1 1 0 0 1 1-1h7.8l8.2 8.2a1.4 1.4 0 0 1 0 2l-6.8 6.8a1.4 1.4 0 0 1-2 0zM8 8h.01',
  send: 'M20.5 3.5 10.5 13.5M20.5 3.5l-6 17-4-7-7-4z',
  eye: 'M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12zM12 14.8a2.8 2.8 0 1 0 0-5.6 2.8 2.8 0 0 0 0 5.6z',
  edit: 'M4 20h4.5L19.3 9.2a2.1 2.1 0 0 0-3-3L5.5 17v3zM14.5 8l3 3',
  shield: 'M12 3 4.5 6v5.5c0 4.7 3.2 8.2 7.5 9.5 4.3-1.3 7.5-4.8 7.5-9.5V6zM8.8 12l2.2 2.2 4.2-4.4',
  link: 'M10 14a4.2 4.2 0 0 0 6 0l3-3a4.2 4.2 0 0 0-6-6l-1.2 1.2M14 10a4.2 4.2 0 0 0-6 0l-3 3a4.2 4.2 0 0 0 6 6l1.2-1.2',
  copy: 'M8.5 8.5h11v11h-11zM15.5 8.5v-4h-11v11h4',
  download: 'M12 3.5v11M7.5 10l4.5 4.5 4.5-4.5M4.5 16.5v3.5h15v-3.5',
  external: 'M13.5 4.5h6v6M19.5 4.5l-8 8M17.5 13.5v6h-13v-13h6',
  qr: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2.5v2.5H14zM17.5 17.5H20V20h-2.5zM14 20h.01M20 14h.01M6.8 6.8h.4v.4h-.4zM16.8 6.8h.4v.4h-.4zM6.8 16.8h.4v.4h-.4z',
  coins: 'M9 10.5c3.3 0 6-1.3 6-3s-2.7-3-6-3-6 1.3-6 3 2.7 3 6 3zM3 7.5v4c0 1.7 2.7 3 6 3M3 11.5v4c0 1.7 2.7 3 6 3M21 12.5c0 1.7-2.7 3-6 3s-6-1.3-6-3 2.7-3 6-3 6 1.3 6 3zM9 12.5v4c0 1.7 2.7 3 6 3s6-1.3 6-3v-4',
} as const

export type IconName = keyof typeof PATHS

interface IconProps {
  name: IconName
  size?: number
  className?: string
  /** Only for the rare icon that stands alone and carries meaning; otherwise it stays hidden. */
  label?: string
}

export function Icon({ name, size = 20, className, label }: IconProps) {
  return (
    <svg
      className={className ? `icon ${className}` : 'icon'}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  )
}
