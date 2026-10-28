export type SyncUnsupportedSummary = Record<string, number>;

const KIND_LABELS: Record<string, { one: string; many: string }> = {
  attachment: { one: 'adjunto recibido por sincronización que aún no está en este dispositivo', many: 'adjuntos recibidos por sincronización que aún no están en este dispositivo' },
};

export function describeUnsupported(unsupported: number, kinds?: SyncUnsupportedSummary): string {
  if (unsupported <= 0) return 'Todo el contenido de tus dispositivos está replicado.';
  const entries = Object.entries(kinds ?? {}).filter(([, count]) => count > 0);
  if (entries.length === 0) {
    return `${unsupported} ${unsupported === 1 ? 'elemento sin contenido completo' : 'elementos sin contenido completo'} en este dispositivo.`;
  }
  const parts = entries.map(([kind, count]) => {
    const label = KIND_LABELS[kind];
    if (!label) return `${count} ${count === 1 ? 'elemento' : 'elementos'} sin contenido completo`;
    return `${count} ${count === 1 ? label.one : label.many}`;
  });
  return `${parts.join(' y ')}.`;
}

export function hasUnsupported(unsupported: number): boolean {
  return unsupported > 0;
}
