/**
 * Labelled text input or textarea. Purple focus ring, mono font, glass fill.
 */
export interface FormFieldProps {
  label?: string;
  type?: string;
  /** Render a multi-line textarea instead of an input */
  textarea?: boolean;
  rows?: number;
  name?: string;
  placeholder?: string;
  value?: string;
  onChange?: (e: any) => void;
  style?: any;
}
