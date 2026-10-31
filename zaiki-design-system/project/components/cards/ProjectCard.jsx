import React from "react";
import { Tag } from "../core/Tag.jsx";

export function ProjectCard({ title, description, tags = [], image, gradient = 1, href }) {
  return (
    <a className="project-card-modern" href={href || "#"} style={{ fontFamily: "var(--font-mono)" }}>
      <div className={`project-visual bg-gradient-${gradient}`}>
        {image && <img className="project-thumb" src={image} alt={`${title} preview`} loading="lazy" />}
      </div>
      <div className="project-details">
        {tags.length > 0 && (
          <div className="project-tags">
            {tags.map((t, i) => (
              <Tag key={i} category={t.category}>{t.label}</Tag>
            ))}
          </div>
        )}
        <h4>{title}</h4>
        {description && <p>{description}</p>}
      </div>
    </a>
  );
}
