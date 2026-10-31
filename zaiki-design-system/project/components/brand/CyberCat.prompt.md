The brand's signature interactive mascot — a purple-eared cyber cat whose eyes follow the cursor and whose face reacts to hover and clicks.

```jsx
<CyberCat size={120} hint="Meow!" />
```

Behavior: hover → happy (blush + smile), click → random playful mood (angry / annoyed / startled), spam-click (4×) → flees off-screen with squash-and-stretch then bounces back. Fully self-contained (injects its own styles). Great as a footer easter egg. Respects `prefers-reduced-motion`.
