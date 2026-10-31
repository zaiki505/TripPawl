Glass card for bento grids — dark translucent bg, backdrop blur, 18px radius; hover lifts it and tints the border purple.

```jsx
<BentoCard main title="Who I Am" href="about.html" linkText="Read full story →">
  I'm a passionate developer who loves creating clean and simple designs.
</BentoCard>
<BentoCard title="Core Tools" chips={["HTML", "CSS", "JavaScript"]} />
```

`main` adds the purple radial wash (use on the grid's large span-2 card). Typical grid: `display:grid; grid-template-columns:1.6fr 1fr; gap:1.25rem` with main card `grid-row: span 2`.
