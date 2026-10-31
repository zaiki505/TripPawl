/**
 * Tiny uppercase category tag pill, hue-coded per project category.
 */
export interface TagProps {
  /** Category hue: web = blue, system = purple, creative = orange, personal = green */
  category?: "web" | "system" | "creative" | "personal";
  children?: any;
}
