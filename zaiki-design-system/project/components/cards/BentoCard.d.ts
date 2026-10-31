/**
 * Glass bento card (18px radius, blur 12, hairline border). Lifts 4px and
 * tints its border purple on hover. Used in the About bento grid.
 * @startingPoint section="Cards" subtitle="Glass bento card with purple hover" viewport="700x260"
 */
export interface BentoCardProps {
  title?: string;
  /** Adds the purple radial wash used on the large main card */
  main?: boolean;
  /** Makes the whole card a link */
  href?: string;
  /** Purple "Read full story →"-style footer link text */
  linkText?: string;
  /** Accent chips row (e.g. core tools) */
  chips?: string[];
  children?: any;
  style?: any;
}
