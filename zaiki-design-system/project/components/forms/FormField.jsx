import React from "react";

export function FormField({ label, type = "text", textarea = false, rows = 4, name, placeholder, value, onChange, style }) {
  return (
    <div className="z-field" style={{ marginBottom: "1rem", ...style }}>
      {label && <label htmlFor={name}>{label}</label>}
      {textarea ? (
        <textarea id={name} name={name} rows={rows} placeholder={placeholder} value={value} onChange={onChange} />
      ) : (
        <input id={name} name={name} type={type} placeholder={placeholder} value={value} onChange={onChange} />
      )}
    </div>
  );
}
