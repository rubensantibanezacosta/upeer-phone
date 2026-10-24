export const FORWARD_EXCERPT_LENGTH = 120;

export function forwardExcerpt(content: string): string {
  const flat = content.replace(/\s+/g, ' ').trim();
  return flat.length > FORWARD_EXCERPT_LENGTH ? `${flat.slice(0, FORWARD_EXCERPT_LENGTH)}…` : flat;
}

export function forwardResultMessage(forwarded: number, failed: number): string {
  if (forwarded === 0) return failed > 0 ? 'No se pudo reenviar el mensaje' : 'Elige un destinatario';
  return failed === 0
    ? forwarded === 1 ? 'Reenviado a 1 contacto' : `Reenviado a ${forwarded} contactos`
    : `Reenviado a ${forwarded}; falló ${failed}`;
}
