/**
 * Floating frosted pill navigation with the brand's gliding active-page
 * indicator (bouncy travel + squish-stretch while moving), circular logo,
 * and Theme toggle button.
 * @startingPoint section="Navigation" subtitle="Frosted nav with gliding squishy indicator" viewport="900x140"
 */
export interface NavPillProps {
  links?: { label: string; href?: string }[];
  /** Label of the active page (gets purple text + indicator pill) */
  active?: string;
  /** Circular logo image URL */
  logo?: string;
  logoText?: string;
  onNavigate?: (label: string, e: any) => void;
  showTheme?: boolean;
  onThemeToggle?: () => void;
  style?: any;
}
