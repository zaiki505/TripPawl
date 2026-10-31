/**
 * Purple-tinted pill breadcrumb for wayfinding on subpages.
 */
export interface BreadcrumbProps {
  /** Ancestor pages, in order */
  items?: { label: string; href?: string }[];
  /** Current page label (purple, bold) */
  current: string;
}
