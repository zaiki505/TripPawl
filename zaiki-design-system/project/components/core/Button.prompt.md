Pill-shaped brand button with the signature bouncy squish-on-click; use `primary` for the one main CTA, `pill` for link rows, `action` for utility actions.

```jsx
<Button variant="primary" href="#projects">View My Work</Button>
<Button variant="secondary">Get in Touch</Button>
<Button variant="pill" href="mailto:...">Email Me</Button>
```

Variants: `primary` (solid #a754ff, purple glow, lifts on hover), `secondary` (dark glass), `pill` (subtle chip that tints purple on hover), `action` (rectangular, 8px radius), `theme` (compact toggle). All squish (`uiPop`) on click — pass `squishy={false}` to opt out.
