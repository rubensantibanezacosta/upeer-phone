export type HighlightSegment = { text: string; match: boolean };

type NormalizedText = { value: string; positions: number[] };

const COMBINING = /[\u0300-\u036f]/g;

function normalizeWithPositions(text: string): NormalizedText {
  let value = '';
  const positions: number[] = [];
  for (let index = 0; index < text.length; index += 1) {
    const stripped = text[index].normalize('NFD').replace(COMBINING, '').toLowerCase();
    for (const character of stripped) {
      value += character;
      positions.push(index);
    }
  }
  return { value, positions };
}

export function highlightSegments(text: string, query: string): HighlightSegment[] {
  const needle = query.trim().normalize('NFD').replace(COMBINING, '').toLowerCase();
  if (!needle || !text) return text ? [{ text, match: false }] : [];

  const { value, positions } = normalizeWithPositions(text);
  const segments: HighlightSegment[] = [];
  let cursor = 0;
  let found = value.indexOf(needle);
  while (found >= 0) {
    const start = positions[found];
    const end = positions[found + needle.length - 1] + 1;
    if (start > cursor) segments.push({ text: text.slice(cursor, start), match: false });
    segments.push({ text: text.slice(start, end), match: true });
    cursor = end;
    found = value.indexOf(needle, found + needle.length);
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), match: false });
  return segments;
}
