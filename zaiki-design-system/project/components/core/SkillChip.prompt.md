Pill chip for listing skills/tools; a `tier` adds the color-coded left border from the skills page.

```jsx
<SkillChip tier="advanced">HTML</SkillChip>
<SkillChip tier="intermediate">Java</SkillChip>
<SkillChip>Git</SkillChip>
```

Tiers: `advanced` (green #10b981), `intermediate` (yellow #eab308), `beginner` (orange #f97316). Chips lift 2px on hover. Wrap groups in a flex row with 0.65rem gap.
