# Portfolio UI Kit

Interactive recreation of the Zaiki portfolio homepage — the brand's one product.

- `index.html` — mount point (loads the design-system bundle + `PortfolioHome.jsx`).
- `PortfolioHome.jsx` — composes DS components: `NavPill`, `Button`, `BentoCard`, `ProjectCard`, `CyberCat`.

**Screens/sections**: floating pill nav (gliding squishy indicator + working theme toggle) · hero with drifting aurora + shimmer highlight + bouncing scroll hint · bento About grid · Featured Projects grid · glass contact banner · footer with the interactive Cyber Cat easter egg.

Interactions: theme toggle cross-fades the page dark↔light; nav links smooth-scroll to sections; every pill squishes on click; hover the cat (happy), click it (moods), spam-click it (it flees).

Source of truth: `reference/site/index.html` + `reference/site/css/`.
