export type SelectableMessage = {
  id: string;
  isMine: boolean;
  deleted?: boolean;
};

export function toggleSelected(current: ReadonlySet<string>, id: string): Set<string> {
  const next = new Set(current);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

export function describeSelection(total: number): string {
  if (total === 0) return 'Ningún mensaje';
  return total === 1 ? '1 seleccionado' : `${total} seleccionados`;
}

export function orderedSelection<T extends { id: string }>(messages: readonly T[], selected: ReadonlySet<string>): T[] {
  return messages.filter((message) => selected.has(message.id));
}

export function deletableIds(messages: readonly SelectableMessage[], selected: ReadonlySet<string>): string[] {
  return orderedSelection(messages, selected)
    .filter((message) => message.isMine && message.deleted !== true)
    .map((message) => message.id);
}

export function forwardableMessages<T extends SelectableMessage & { timestamp: number }>(messages: readonly T[], selected: ReadonlySet<string>): T[] {
  return orderedSelection(messages, selected)
    .filter((message) => message.deleted !== true)
    .map((message, index) => ({ message, index }))
    .sort((left, right) => (left.message.timestamp === right.message.timestamp
      ? left.index - right.index
      : left.message.timestamp - right.message.timestamp))
    .map((entry) => entry.message);
}

export function selectionExcerpt(contents: readonly string[], limit = 240): string {
  const joined = contents.join('\n').trim();
  return joined.length <= limit ? joined : `${joined.slice(0, limit).trimEnd()}…`;
}
