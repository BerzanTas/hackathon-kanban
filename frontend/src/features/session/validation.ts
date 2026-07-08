// Lightweight client-side checks. The backend performs authoritative validation;
// these only catch obvious mistakes before a round-trip.

export function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
}

export function minLength(value: string, min: number): boolean {
  return value.length >= min
}
