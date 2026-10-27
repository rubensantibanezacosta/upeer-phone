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
