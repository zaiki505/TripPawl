Floating frosted-glass pill nav; the purple indicator pill glides to the active link with bounce and squish-stretches while travelling.

```jsx
<NavPill
  logo="assets/logo.jpeg"
  active={page}
  links={[{ label: "About Me" }, { label: "Education" }, { label: "Projects" }, { label: "Skills" }, { label: "Contact" }]}
  onNavigate={setPage}
  onThemeToggle={() => document.body.classList.toggle("light")}
/>
```

Position it fixed top-center in real pages (`position:fixed; top:1.5rem; left:50%; transform:translateX(-50%)`). Links squish on click.
