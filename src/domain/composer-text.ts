export type TextSelection = { end: number; start: number };

export type TextInsertion = { selection: TextSelection; value: string };

export function insertAtSelection(value: string, selection: TextSelection, insert: string): TextInsertion {
  const length = value.length;
  const start = Math.min(Math.max(selection.start, 0), length);
  const end = Math.min(Math.max(selection.end, start), length);
  const cursor = start + insert.length;
  return {
    selection: { end: cursor, start: cursor },
    value: `${value.slice(0, start)}${insert}${value.slice(end)}`,
  };
}

export function deleteBeforeSelection(value: string, selection: TextSelection): TextInsertion {
  const length = value.length;
  const start = Math.min(Math.max(selection.start, 0), length);
  const end = Math.min(Math.max(selection.end, start), length);
  if (end > start) {
    return { selection: { end: start, start }, value: `${value.slice(0, start)}${value.slice(end)}` };
  }
  if (start === 0) return { selection: { end: 0, start: 0 }, value };
  const head = Array.from(value.slice(0, start));
  head.pop();
  const kept = head.join('');
  return { selection: { end: kept.length, start: kept.length }, value: `${kept}${value.slice(start)}` };
}
