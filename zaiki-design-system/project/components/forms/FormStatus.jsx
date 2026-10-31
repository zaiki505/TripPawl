import React from "react";

export function FormStatus({ state = "success", visible = true, children }) {
  return (
    <div className={`form-status${visible ? " is-visible" : ""}`} data-state={state} role="status">
      {children}
    </div>
  );
}
