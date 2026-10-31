/**
 * Inline success/error status banner that slides + fades in below a form.
 */
export interface FormStatusProps {
  state?: "success" | "error";
  /** Toggles the slide/fade-in animation */
  visible?: boolean;
  children?: any;
}
