/**
 * Skill chip pill; optional tier adds a colored left border
 * (green/yellow/orange for advanced/intermediate/beginner).
 */
export interface SkillChipProps {
  /** Skill tier — colors the left border. Omit for a plain chip. */
  tier?: "advanced" | "intermediate" | "beginner";
  children?: any;
}
