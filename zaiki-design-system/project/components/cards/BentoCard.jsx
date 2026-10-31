import React from "react";

export function BentoCard({ title, children, main = false, href, linkText, chips, style }) {
  const Comp = href ? "a" : "div";
  return (
    <Comp
      href={href}
      className={`bento-card${main ? " bento-card-main" : ""}`}
      style={{ fontFamily: "var(--font-mono)", ...style }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "0.65rem", height: "100%" }}>
        {title && (
          <h3 style={{ fontSize: "clamp(1.1rem, 2vw, 1.45rem)", fontWeight: "bold", color: "var(--text-primary)", margin: 0 }}>
            {title}
          </h3>
        )}
        {children && (
          <div style={{ fontSize: "var(--text-body-size)", color: "var(--text-body)", lineHeight: 1.7, flex: 1 }}>
            {children}
          </div>
        )}
        {chips && chips.length > 0 && (
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", marginTop: "0.5rem" }}>
            {chips.map((c, i) => (
              <span key={i} className="accent-chip">{c}</span>
            ))}
          </div>
        )}
        {linkText && (
          <span style={{ marginTop: "auto", fontSize: "0.85rem", fontWeight: "bold", color: "var(--highlight)", letterSpacing: "0.02em" }}>
            {linkText}
          </span>
        )}
      </div>
    </Comp>
  );
}
