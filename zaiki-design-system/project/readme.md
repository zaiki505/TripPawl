# Zaiki Design System

Design system for **Zaiki** — the personal brand of **Muhd Uzair** (nickname "zaiki"), a Computer Science (Interactive Media) student at UTeM, Malaysia. His flagship surface is a personal developer portfolio: a dark-first, purple-accented, all-monospace site built with vanilla HTML/CSS/JS, famous (locally, at least) for its bouncy squish animations and an interactive Cyber Cat mascot in the footer.

**Sources**
- GitHub: [zaiki505/Zaiki-Personal-Portfolio](https://github.com/zaiki505/Zaiki-Personal-Portfolio) — full source imported under `reference/site/` (HTML pages, `css/`, `script.js`). Explore that repo/folder for deeper detail; it is the ground truth for every value here.
- Live site: [zaiki.netlify.app](https://zaiki.netlify.app/)

**One product**: the portfolio website (Home, About, Education, Projects, Skills, Contact, Resume, 404 + project detail pages).

---

## CONTENT FUNDAMENTALS

- **Voice**: first-person "I", talking directly to "you". Friendly, humble, a little playful — never corporate. Example hero greeting: `Hi there 👋, I'm` → `Muhd Uzair`.
- **Playful copy with light puns**: "Currently heads-down (pun intended) as a Computer Science student — but genuinely open to collaboration ideas and a good conversation." / "Got an idea or just want to talk tech? Give a heads up!"
- **Casing**: sentence case for body; Title Case for headings/nav ("About Me", "Featured Projects", "View My Work"); UPPERCASE reserved for kickers and tag pills.
- **Emoji**: used sparingly and deliberately — 👋 in the hero greeting, 🌙/☀️ as compact theme icons. Not scattered through body copy.
- **CTAs are short verb phrases**: "View My Work", "Get in Touch", "Email Me", "Read full story →". Arrows written as `→` / `&rarr;`.
- **Descriptions are plain and concrete**: one or two sentences, no buzzword salad. "A command-line movie tracker to manage data dynamically using linked lists."
- **Self-description vocabulary**: "clean", "simple", "functional", "passionate", "eager to learn". Tagline: *Web & App Developer · Designer · Creator* (middot separators).

## VISUAL FOUNDATIONS

- **Color**: dark-first. Page bg `lab(from #333 12 a b)` (≈`#141418`), raised bg L20 (≈`#20202a`). ONE accent: purple `#a754ff` (`--highlight`) used for kickers, links, active nav, section titles, glows. Gradient partners `#d48cff`, `#c2a0f5`, `#7b5bff`. Light theme (`body.light`) flips to `lab(from white 97 a b)` ≈ `#f6f6f8`. Status greens/reds (`#00e676`/`#ff1744`) and skill-tier colors (green/yellow/orange) are the only other hues, plus per-category tag hues (blue/purple/orange/green).
- **The source derives shades with relative `lab()` color** (`lab(from var(--highlight) 40 a b)`) with hex fallbacks — keep doing this rather than inventing new hexes.
- **Type**: JetBrains Mono for EVERYTHING (variable 100–800 + italics, Google Fonts). Bold headings, fluid `clamp()` sizes, tight 1.05 line-height on display, 1.6–1.7 on body. Kickers: bold uppercase purple. Gradient-clipped text for hero highlight word (animated shimmer).
- **Backgrounds**: flat dark base + slow drifting purple/pink/blue **aurora** blobs (blur 60px, `heroAurora` 7s alternate) + faint SVG **film grain** overlay (opacity 0.05) on heroes. No photos as backgrounds.
- **Glassmorphism everywhere**: cards are `rgba(0,0,0,0.32–0.45)` + `backdrop-filter: blur(10–12px)` + 1px hairline border `rgba(255,255,255,0.07–0.08)`. Light theme swaps to solid white cards.
- **Radii**: pills `999px` (nav, chips, tags, CTAs, breadcrumb); cards `18px`; panels `12px`; banner `24px`; nav shell `50px`; buttons `8px`.
- **Shadows**: soft, large, low-alpha black (`0 10px 24px rgba(0,0,0,0.18)` → deeper on hover). Purple glow shadows on accent elements (`0 4px 20px rgba(167,84,255,0.4)`).
- **Animation — the signature**: bouncy + squishy.
  - Global **`uiPop` squish** on every click (`.ui-clicked`, 0.4s `cubic-bezier(0.175,0.885,0.32,1.275)`).
  - Bounce overshoot easing `cubic-bezier(0.34,1.56,0.64,1)` for glides/pops; `cubic-bezier(0.16,1,0.3,1)` for entrances.
  - Entrances combine **fade + translateY + blur(12–16px)→0**, staggered per section (~0.08s steps).
  - Nav has a **gliding pill indicator** that squish-stretches (`navPillSquish`) while travelling between links.
  - Hero title runs a looping **blur-wave** across characters; idle elements **bob** (`peekCardBob`).
  - Everything respects `prefers-reduced-motion`.
- **Hover states**: cards lift `translateY(-4/-5px)` + deepen shadow + border tints purple; links/text turn purple; chips lift 2px; primary CTA lightens to `#c893fa`. **Press states**: shrink/squish (`scale(0.9–0.95)` or uiPop).
- **Theme toggle**: dark default; whole page cross-fades 0.5s ease-in-out on toggle (temporary `theme-transition` class).
- **Layout**: content column `min(1120px, 100% - 2rem)`; floating pill nav fixed top-center; generous `clamp()`-based section padding; bento grid (1.6fr/1fr) for about; 3-col project grid.
- **Easter eggs & micro-delights are a brand pillar**: interactive footer Cyber Cat (eyes follow cursor, happy on hover, angry on click, flees with squash-and-stretch when spam-clicked; pupils dilate in dark theme, slit in light), coin-flip profile photo, peek-cards that morph into the bento grid, logo tooltip pill, scroll-hint bounce. New designs should include at least one small thoughtful surprise.

## ICONOGRAPHY

- **No icon font or icon library.** Icons are minimal **inline SVGs, stroke-based** (`stroke="currentColor"`, stroke-width 2–2.2, round caps/joins): hamburger lines, chevron scroll hint, arrow-up scroll-to-top. Match this style if new glyphs are needed (Lucide is the nearest CDN match — flag it if used).
- **Emoji as icons** in tiny doses: 🌙/☀️ on the compact theme button.
- **Unicode arrows** `→` as link affordances.
- **The Cyber Cat** is a hand-built inline SVG mascot (fur `#423b4f`, purple inner ears, pink nose `#db68d7`) — see `reference/site/css/misc.css` + `script.js`, and the `CyberCat` component.
- **Brand marks**: `assets/logo.jpeg` / `assets/favicon.png` (round avatar mark), `assets/pf2.png` and `assets/profile-photo.jpg` (profile photos). Logo renders as a circular image in the nav. No wordmark file — the wordmark is just "zaiki's Portfolio" in JetBrains Mono.

## Index

- `styles.css` — global entry; imports everything under `tokens/`.
- `tokens/` — `colors.css`, `typography.css`, `surfaces.css`, `motion.css`, `components.css` (tokenized component classes matching source class names).
- `components/` — React primitives (each with `.d.ts`, `.prompt.md`, card HTML):
  - `core/` — **Button** (primary/secondary/pill/action/theme), **Tag**, **SkillChip**, **Breadcrumb**
  - `cards/` — **BentoCard**, **ProjectCard**, **TimelineItem**
  - `navigation/` — **NavPill** (gliding squishy indicator), **ScrollTopButton**
  - `forms/` — **FormField**, **FormStatus**
  - `brand/` — **CyberCat** (interactive mascot)
- `ui_kits/portfolio/` — interactive homepage recreation (`index.html`).
- `guidelines/` — foundation specimen cards for the Design System tab.
- `assets/` — logo, favicon, profile photos, project mockup thumbnails (`assets/CareerReady/…` etc.).
- `reference/site/` — the full imported portfolio source (ground truth).
- `SKILL.md` — agent skill entry point.

**Notes / intentional details**
- Fonts ship via Google Fonts `@import` (the repo has no font binaries). If offline use is needed, ask the user for JetBrains Mono woff2 files.
- The `uiPop` squish is applied via a tiny JS pattern (add `.ui-clicked` on click, remove on `animationend`) — components do this internally.
