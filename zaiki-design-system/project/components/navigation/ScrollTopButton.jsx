import React from "react";

export function ScrollTopButton({ onClick, style }) {
  const handle = (e) => {
    const el = e.currentTarget;
    el.classList.remove("ui-clicked");
    void el.offsetWidth;
    el.classList.add("ui-clicked");
    el.addEventListener("animationend", () => el.classList.remove("ui-clicked"), { once: true });
    if (onClick) onClick(e);
    else window.scrollTo({ top: 0, behavior: "smooth" });
  };
  return (
    <button className="scroll-top-btn" aria-label="Scroll to top" onClick={handle} style={style}>
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="6 15 12 9 18 15"></polyline>
      </svg>
    </button>
  );
}
