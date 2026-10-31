/**
 * Academic/history timeline row: purple year column + glass panel with
 * optional institution logo tile.
 */
export interface TimelineItemProps {
  /** Year range, e.g. "2023 – Present" (rendered bold purple) */
  year: string;
  title?: string;
  /** Small uppercase purple label above the title */
  kicker?: string;
  /** Institution logo image URL (white 80px rounded tile) */
  logo?: string;
  children?: any;
}
