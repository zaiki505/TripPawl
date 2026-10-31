Labelled input/textarea with the brand's purple focus ring and mono type.

```jsx
<FormField label="Name" name="name" placeholder="Your name" />
<FormField label="Message" name="msg" textarea rows={5} />
```

Focus tints border purple + adds a soft `rgba(167,84,255,0.12)` ring. Pass `textarea` for the multiline variant.
