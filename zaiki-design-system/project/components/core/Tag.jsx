import React from "react";

export function Tag({ category = "system", children }) {
  return <span className={`tag tag-${category}`}>{children}</span>;
}
