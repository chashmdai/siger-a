import type {LucideIcon} from 'lucide-react';
import {Activity, ArrowLeft, ArrowLeftRight, ArrowRight, ArrowRightToLine, BadgeCheck, Bell, Bookmark, BriefcaseBusiness, Calendar, CalendarRange, ChartBar, ChartBarBig,
  ChartBarStacked, ChartColumn, ChartColumnBig, ChartLine, ChartNoAxesCombined, ChartPie, Check, ChevronDown, ChevronLeft, ChevronRight, CircleCheck, Clock,
  ClockExclamationPoint, Columns3, Database, Download, Eye, EyeOff, FileArchive, FileChartColumn, FileCode, FileImage, FileSpreadsheet, FileText, Flag, Folder,
  FolderCheck, GitBranch, Heart, House, Inbox, Info, LayoutDashboard, Layers, Lightbulb, Link, Lock, LogOut, Mail, MapPin, Menu, MessageSquare, Milestone,
  MoveRight, PanelLeft, Pencil, Plus, Printer, RefreshCw, RotateCcwClock, Rows3, Scale, Search, Send, ShieldCheck, Sigma, SlidersHorizontal, Snowflake, Sparkles,
  SquareCheckBig, Tag, TrendingDown, TrendingUp, TriangleExclamationPoint, Upload, User, UserPlus, Users, X} from 'lucide-react';

/**
 * Iconos de la aplicacion (0.8.1, D54): cada nombre propio de SIGER se dibuja con un icono de lucide-react (ISC), importado
 * por nombre. Este es el unico archivo que importa lucide-react. Son apoyo visual: cada uso va acompanado de texto o de
 * aria-label en el control, y el icono queda siempre oculto a los lectores de pantalla. El emblema institucional nunca
 * reemplaza un icono operativo.
 */
const ICONS = {
  home: House, attention: UserPlus, people: Users, inbox: Inbox, mail: Mail, send: Send, folder: Folder, briefcase: BriefcaseBusiness, tasks: SquareCheckBig,
  flow: GitBranch, bell: Bell, swap: ArrowLeftRight, shield: ShieldCheck, activity: Activity, logout: LogOut, refresh: RefreshCw, search: Search, left: ChevronLeft,
  right: ChevronRight, down: ChevronDown, close: X, menu: Menu, info: Info, alert: TriangleExclamationPoint, check: Check, checkCircle: CircleCheck, clock: Clock,
  upload: Upload, download: Download, file: FileText, eye: Eye, eyeOff: EyeOff, printer: Printer, arrowRight: ArrowRight, arrowLeft: ArrowLeft, plus: Plus,
  edit: Pencil, user: User, lock: Lock, message: MessageSquare, flag: Flag, calendar: Calendar, link: Link, history: RotateCcwClock, handoff: ArrowRightToLine,
  layers: Layers, heart: Heart, chart: ChartColumn, sparkle: Sparkles,
  // Explorador estadistico (0.8.1, seccion 3 del plan)
  folderCheck: FolderCheck, badgeCheck: BadgeCheck, clockAlert: ClockExclamationPoint, trendUp: TrendingUp, trendDown: TrendingDown, moveRight: MoveRight,
  dashboard: LayoutDashboard, fileChart: FileChartColumn, chartCombined: ChartNoAxesCombined, fileSpreadsheet: FileSpreadsheet, fileArchive: FileArchive,
  fileImage: FileImage, fileCode: FileCode, chartBar: ChartBarBig, chartGrouped: ChartBar, chartStacked: ChartBarStacked, chartPaired: ChartColumnBig,
  chartLine: ChartLine, chartPie: ChartPie, calendarRange: CalendarRange, mapPin: MapPin, scale: Scale, milestone: Milestone, tag: Tag, sliders: SlidersHorizontal,
  rows: Rows3, columns: Columns3, snowflake: Snowflake, sigma: Sigma, lightbulb: Lightbulb, bookmark: Bookmark, panel: PanelLeft, database: Database,
} satisfies Record<string, LucideIcon>;
export type IconName = keyof typeof ICONS;
/** Todos los nombres, para las pruebas. */
export const ICON_NAMES = Object.keys(ICONS) as IconName[];

export function Icon({name, size = 20, className}: {name: IconName; size?: number; className?: string}) {
  const Glyph = ICONS[name];
  return <Glyph className={className} size={size} strokeWidth={1.8} absoluteStrokeWidth={false} aria-hidden="true" focusable="false"/>;
}
