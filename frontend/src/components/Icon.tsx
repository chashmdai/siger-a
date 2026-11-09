/**
 * Iconos de trazo propios (24x24, currentColor). Son apoyo visual: cada uso va acompanado de texto
 * o de aria-label en el control. El emblema institucional nunca reemplaza un icono operativo.
 */
const PATHS = {
  home: ['M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'],
  attention: ['M9 11.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M2 21v-.5A6.5 6.5 0 0 1 8.5 14h1A6.5 6.5 0 0 1 16 20.5v.5', 'M19 8v6', 'M16 11h6'],
  people: ['M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z', 'M2.5 20a6.5 6.5 0 0 1 13 0', 'M16 4.3a3.5 3.5 0 0 1 0 6.4', 'M18.2 14.2A6.5 6.5 0 0 1 21.5 20'],
  inbox: ['M3 13.5 6 5h12l3 8.5V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z', 'M3 13.5h5l1.5 2.5h5l1.5-2.5h5'],
  mail: ['M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z', 'M3.5 6.5 12 13l8.5-6.5'],
  send: ['M21 3 10 14', 'M21 3l-6.5 18-4.5-7-7-4.5z'],
  folder: ['M3 6.5A1.5 1.5 0 0 1 4.5 5h4.3l2 2.5h8.7A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z'],
  briefcase: ['M4 7.5h16a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1z', 'M8.5 7.5V5.5a1.5 1.5 0 0 1 1.5-1.5h4a1.5 1.5 0 0 1 1.5 1.5v2', 'M3 13h18'],
  tasks: ['M6.5 3.5h11a3 3 0 0 1 3 3v11a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3v-11a3 3 0 0 1 3-3z', 'M8 12.5l2.8 2.8L16 9.8'],
  flow: ['M6 8.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z', 'M6 20.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z', 'M18 10.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z', 'M6 8.5v7', 'M18 10.5c0 4-5 4-10.2 6'],
  bell: ['M6 9.5a6 6 0 0 1 12 0c0 5.5 2.5 7 2.5 7h-17S6 15 6 9.5z', 'M10 20a2 2 0 0 0 4 0'],
  swap: ['M7 4 3 8l4 4', 'M3 8h13', 'M17 20l4-4-4-4', 'M21 16H8'],
  shield: ['M12 3l8 3v6c0 4.6-3.4 8.2-8 9-4.6-.8-8-4.4-8-9V6z', 'M9 12l2.2 2.2L15.5 10'],
  activity: ['M3 12h4l3-8 4 16 3-8h4'],
  logout: ['M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3', 'M10 17l-5-5 5-5', 'M5 12h11'],
  refresh: ['M20 11a8 8 0 0 0-14.8-4L3 9.5', 'M3 4v5.5h5.5', 'M4 13a8 8 0 0 0 14.8 4l2.2-2.5', 'M21 20v-5.5h-5.5'],
  search: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'M20 20l-4-4'],
  left: ['M15 18l-6-6 6-6'],
  right: ['M9 18l6-6-6-6'],
  down: ['M6 9l6 6 6-6'],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 11v5', 'M12 7.8v.2'],
  alert: ['M10.3 4.2 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z', 'M12 9.5v4', 'M12 16.8v.2'],
  check: ['M5 12.5l4.5 4.5L19 7.5'],
  checkCircle: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M8 12.3l2.8 2.8L16.2 9.6'],
  clock: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 7v5l3.2 2'],
  upload: ['M12 15V4', 'M7 9l5-5 5 5', 'M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4'],
  download: ['M12 4v11', 'M7 10l5 5 5-5', 'M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4'],
  file: ['M6 3h8l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z', 'M14 3v5h5', 'M8.5 13h7', 'M8.5 17h4.5'],
  eye: ['M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'],
  eyeOff: ['M3 3l18 18', 'M10.6 6.1A10 10 0 0 1 12 6c6.4 0 10 6 10 6a17 17 0 0 1-3.2 3.9', 'M6.6 6.6C3.7 8.4 2 12 2 12s3.6 7 10 7a9.8 9.8 0 0 0 5.4-1.6', 'M9.9 9.9a3 3 0 0 0 4.2 4.2'],
  printer: ['M7 9V3h10v6', 'M5 9h14a2 2 0 0 1 2 2v5a1 1 0 0 1-1 1h-3', 'M7 17H4a1 1 0 0 1-1-1v-5a2 2 0 0 1 2-2', 'M7 14h10v7H7z'],
  arrowRight: ['M5 12h14', 'M13 6l6 6-6 6'],
  arrowLeft: ['M19 12H5', 'M11 6l-6 6 6 6'],
  plus: ['M12 5v14', 'M5 12h14'],
  edit: ['M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z', 'M13.5 6.5l4 4'],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'],
  lock: ['M6 10h12a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z', 'M8 10V7a4 4 0 0 1 8 0v3'],
  message: ['M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-5 4V6a1 1 0 0 1 1-1z'],
  flag: ['M5 21V4', 'M5 4h11l-2 4 2 4H5'],
  calendar: ['M4 5h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z', 'M3 10h18', 'M8 3v4', 'M16 3v4'],
  link: ['M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1', 'M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1'],
  history: ['M3 12a9 9 0 1 0 2.6-6.4L3 8', 'M3 3v5h5', 'M12 7.5V12l3 2'],
  handoff: ['M4 12h11', 'M11 8l4 4-4 4', 'M19 5v14'],
  layers: ['M12 3 2 8l10 5 10-5z', 'M2 13l10 5 10-5'],
  heart: ['M12 20s-7.5-4.5-7.5-10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.5 12 20 12 20z'],
  sparkle: ['M12 3v4', 'M12 17v4', 'M3 12h4', 'M17 12h4', 'M6 6l2.5 2.5', 'M15.5 15.5 18 18', 'M18 6l-2.5 2.5', 'M8.5 15.5 6 18'],
} satisfies Record<string, string[]>;
export type IconName = keyof typeof PATHS;

export function Icon({name, size = 20, className}: {name: IconName; size?: number; className?: string}) {
  return <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
    {PATHS[name].map(d => <path key={d} d={d}/>)}
  </svg>;
}
