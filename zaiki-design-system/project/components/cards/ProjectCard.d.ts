/**
 * Project showcase card: image (or gradient) visual on top, category tags,
 * title, one-line description. Whole card is a link; hover lifts + zooms image.
 * @startingPoint section="Cards" subtitle="Project card with tags + visual" viewport="700x420"
 */
export interface ProjectCardProps {
  title: string;
  description?: string;
  /** Category tags shown above the title */
  tags?: { label: string; category?: "web" | "system" | "creative" | "personal" }[];
  /** Thumbnail image URL (covers the visual area) */
  image?: string;
  /** Fallback gradient background 1–5 when no image */
  gradient?: 1 | 2 | 3 | 4 | 5;
  href?: string;
}
