/**
 * Zaiki button. Pill-shaped by default; every click plays the brand's
 * bouncy uiPop squish animation.
 * @startingPoint section="Core" subtitle="Pill CTAs with squishy click" viewport="700x220"
 */
export interface ButtonProps {
  /** Visual variant. primary = solid purple CTA; secondary = dark glass CTA; pill = contact pill; action = 8px-radius rectangular button; theme = compact theme-toggle button. */
  variant?: "primary" | "secondary" | "pill" | "action" | "theme";
  /** Renders an <a> instead of <button> */
  href?: string;
  onClick?: (e: any) => void;
  /** Play the uiPop squish on click (brand default: true) */
  squishy?: boolean;
  children?: any;
  style?: any;
}
