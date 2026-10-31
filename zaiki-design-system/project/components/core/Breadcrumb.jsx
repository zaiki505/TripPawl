import React from "react";

export function Breadcrumb({ items = [], current }) {
  return (
    <nav className="breadcrumb" aria-label="Breadcrumb">
      {items.map((item, i) => (
        <React.Fragment key={i}>
          <a href={item.href || "#"}>{item.label}</a>
          <span className="breadcrumb-sep">/</span>
        </React.Fragment>
      ))}
      <span className="breadcrumb-current">{current}</span>
    </nav>
  );
}
