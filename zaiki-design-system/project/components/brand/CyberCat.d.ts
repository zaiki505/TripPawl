/**
 * The Zaiki Cyber Cat — the brand's interactive footer mascot. Its eyes track
 * the cursor; hovering makes it happy, clicking cycles playful moods (angry /
 * annoyed / startled), and spam-clicking makes it flee off-screen with a
 * squash-and-stretch bounce. A signature easter egg — use it as a delightful
 * surprise, typically in a footer.
 * @startingPoint section="Brand" subtitle="Interactive mascot — eyes follow, moods, flees" viewport="360x360"
 */
export interface CyberCatProps {
  /** Rendered width in px (SVG scales) */
  size?: number;
  /** Caption under the cat; pass "" to hide */
  hint?: string;
}
