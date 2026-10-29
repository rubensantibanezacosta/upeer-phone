export const ONLINE_WINDOW_MS = 65_000;

type PresenceInput = {
  lastActive?: number | null;
  now: number;
  shareActivity: boolean;
};

function toMillis(value?: number | null): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return null;
  return value;
}

export function isOnline(lastActive: number | undefined | null, now: number, windowMs = ONLINE_WINDOW_MS): boolean {
  const active = toMillis(lastActive);
  return active !== null && now - active < windowMs;
}

function formatClock(date: Date): string {
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export function describeLastSeen(lastSeen: number | undefined | null, now: number): string | null {
  const seen = toMillis(lastSeen);
  if (seen === null) return null;
  const date = new Date(seen);
  const minutes = Math.floor((now - seen) / 60_000);
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  if (date.toDateString() === new Date(now).toDateString()) return `hoy a las ${formatClock(date)}`;
  const day = String(date.getDate()).padStart(2, '0');
  return `el ${day} ${MONTHS[date.getMonth()]} a las ${formatClock(date)}`;
}

export function describePresence({ lastActive, now, shareActivity }: PresenceInput): string | null {
  if (!shareActivity) return null;
  const active = toMillis(lastActive);
  if (active === null) return 'Desconectado';
  if (isOnline(active, now)) return 'En línea';
  const label = describeLastSeen(active, now);
  return label ? `Última vez ${label}` : 'Desconectado';
}
