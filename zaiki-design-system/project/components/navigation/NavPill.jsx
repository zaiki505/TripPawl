import React from "react";
const { useRef, useState, useEffect } = React;

function squish(el) {
  if (!el) return;
  el.classList.remove("ui-clicked");
  void el.offsetWidth;
  el.classList.add("ui-clicked");
  el.addEventListener("animationend", () => el.classList.remove("ui-clicked"), { once: true });
}

/* Floating pill nav with the gliding, squish-stretching active indicator. */
export function NavPill({ links = [], active, logo, logoText = "zaiki's Portfolio", onNavigate, showTheme = true, onThemeToggle, style }) {
  const listRef = useRef(null);
  const [indicator, setIndicator] = useState(null);
  const [gliding, setGliding] = useState(false);

  const measure = (label) => {
    const list = listRef.current;
    if (!list) return;
    const el = list.querySelector(`[data-nav="${CSS.escape(label)}"]`);
    if (!el) { setIndicator(null); return; }
    setIndicator({
      x: el.offsetLeft, y: el.offsetTop,
      w: el.offsetWidth, h: el.offsetHeight,
    });
  };

  useEffect(() => {
    measure(active);
    const t = setTimeout(() => measure(active), 300); // after fonts settle
    return () => clearTimeout(t);
  }, [active, links.length]);

  const handleNav = (e, label) => {
    setGliding(true);
    setTimeout(() => setGliding(false), 560);
    if (onNavigate) onNavigate(label, e);
  };

  return (
    <header className="nav-pill" style={{ width: "min(90vw, 1200px)", ...style }}>
      <nav style={{ display: "grid", gridTemplateColumns: "1fr auto 1fr", alignItems: "center", width: "100%", fontFamily: "var(--font-mono)" }}>
        <div style={{ display: "flex", alignItems: "center", justifySelf: "start", gap: "0.5rem" }}>
          {logo && <img src={logo} alt="" style={{ height: "2rem", width: "2rem", borderRadius: "50%", objectFit: "cover" }} />}
          <span style={{ fontSize: "clamp(0.8rem, 1.2vw, 1rem)", whiteSpace: "nowrap" }}>{logoText}</span>
        </div>
        <ul className="nav-links-row" ref={listRef}>
          {indicator && (
            <li aria-hidden="true" style={{
              position: "absolute", listStyle: "none", pointerEvents: "none", zIndex: 0,
              left: 0, top: 0, width: indicator.w, height: indicator.h,
              transform: `translate(${indicator.x}px, ${indicator.y}px)`,
              transition: "transform 0.55s cubic-bezier(0.34,1.56,0.64,1), width 0.55s cubic-bezier(0.34,1.56,0.64,1)",
            }}>
              <span className="nav-indicator-fill" style={gliding ? { animation: "navPillSquish 0.55s cubic-bezier(0.34,1.56,0.64,1)" } : null}></span>
            </li>
          )}
          {links.map((link) => (
            <li key={link.label} style={{ listStyle: "none" }}>
              <a
                className="nav-link"
                data-nav={link.label}
                href={link.href || "#"}
                aria-current={link.label === active ? "page" : undefined}
                onClick={(e) => { if (!link.href) e.preventDefault(); squish(e.currentTarget); handleNav(e, link.label); }}
              >
                {link.label}
              </a>
            </li>
          ))}
        </ul>
        <div style={{ justifySelf: "end" }}>
          {showTheme && (
            <button className="themebutton" onClick={(e) => { squish(e.currentTarget); if (onThemeToggle) onThemeToggle(); }}>
              Theme
            </button>
          )}
        </div>
      </nav>
    </header>
  );
}
