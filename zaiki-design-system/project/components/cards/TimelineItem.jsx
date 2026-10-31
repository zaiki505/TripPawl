import React from "react";

export function TimelineItem({ year, title, kicker, logo, children }) {
  return (
    <div className="timeline-item" style={{ fontFamily: "var(--font-mono)" }}>
      <div className="timeline-year">{year}</div>
      <div style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
        {logo && (
          <div style={{
            width: 80, height: 80, flexShrink: 0, borderRadius: "20%",
            background: "#fff", border: "1px dashed rgba(255,255,255,0.5)",
            display: "flex", alignItems: "center", justifyContent: "center", overflow: "hidden",
          }}>
            <img src={logo} alt="" style={{ width: "85%", height: "85%", objectFit: "contain" }} />
          </div>
        )}
        <div>
          {kicker && (
            <span style={{ color: "var(--highlight)", fontWeight: "bold", textTransform: "uppercase", fontSize: "0.78rem" }}>
              {kicker}
            </span>
          )}
          {title && <h3 style={{ margin: "0 0 0.5rem" }}>{title}</h3>}
          <div style={{ color: "var(--text-body)", fontSize: "var(--text-body-size)" }}>{children}</div>
        </div>
      </div>
    </div>
  );
}
