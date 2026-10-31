Project showcase card — visual on top (image zooms subtly on hover), category tags, bold title, muted one-liner.

```jsx
<ProjectCard
  title="Attention Monitoring Detector"
  description="A machine learning-based solution to monitor user attention."
  tags={[{ label: "Web Development", category: "web" }]}
  image="assets/attention-detector/ai-mockup.png"
  gradient={4}
  href="projects/attention.html"
/>
```

Use in a 3-column grid. `gradient` 1–5 picks the tinted backdrop behind/instead of the image. Real project thumbnails live in `assets/<Project>/`.
