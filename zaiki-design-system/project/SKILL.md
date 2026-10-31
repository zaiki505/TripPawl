---
name: zaiki-design
description: Use this skill to generate well-branded interfaces and assets for Zaiki (Muhd Uzair's personal developer brand), either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files.
If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.
If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.

## Quick map
- `readme.md` — full design guide: content voice, visual foundations, iconography, motion.
- `styles.css` — global entry; `@import`s all token files. Link this one file.
- `tokens/` — colors, typography, surfaces, motion, component classes (CSS custom properties).
- `components/` — React primitives (Button, Tag, SkillChip, Breadcrumb, BentoCard, ProjectCard, TimelineItem, NavPill, ScrollTopButton, FormField, FormStatus, CyberCat). Each has a `.prompt.md` with usage.
- `ui_kits/portfolio/` — the interactive homepage recreation.
- `guidelines/` — foundation specimen cards.
- `assets/` — logo, favicon, profile photos, project thumbnails.
- `reference/site/` — the original imported portfolio source (ground truth).

## Non-negotiables
- ONE accent: purple `#a754ff`. Dark-first; light theme via `body.light`.
- JetBrains Mono for EVERYTHING.
- Glassmorphism cards, generous radii (pills 999px, cards 18px).
- Motion is the fingerprint: bouncy + squishy. Add the `uiPop` click squish and at least one small thoughtful easter egg.
