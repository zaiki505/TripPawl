import React from "react";

export function SkillChip({ tier, children }) {
  return <span className={`skill-chip${tier ? " " + tier : ""}`}>{children}</span>;
}
