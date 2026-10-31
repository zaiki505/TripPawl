import React from "react";

/* Adds the brand's global uiPop squish on click */
function squish(el) {
  if (!el) return;
  el.classList.remove("ui-clicked");
  void el.offsetWidth;
  el.classList.add("ui-clicked");
  el.addEventListener("animationend", () => el.classList.remove("ui-clicked"), { once: true });
}

const VARIANT_CLASS = {
  primary: "cta-primary",
  secondary: "cta-secondary",
  pill: "contact-pill",
  action: "action-btn",
  theme: "themebutton",
};

export function Button({ variant = "primary", href, onClick, children, squishy = true, style, ...rest }) {
  const cls = VARIANT_CLASS[variant] || VARIANT_CLASS.primary;
  const handleClick = (e) => {
    if (squishy) squish(e.currentTarget);
    if (onClick) onClick(e);
  };
  const Comp = href ? "a" : "button";
  return (
    <Comp
      href={href}
      className={cls}
      onClick={handleClick}
      style={style}
      {...rest}
    >
      {children}
    </Comp>
  );
}
