Inline form feedback banner — green for success, red for error — that slides and fades in.

```jsx
<FormStatus state="success" visible>Message sent — I'll get back to you!</FormStatus>
<FormStatus state="error" visible>Something went wrong. Try again.</FormStatus>
```

Toggle `visible` to animate it in/out (max-height + opacity + translate).
