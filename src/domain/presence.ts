export const ONLINE_WINDOW_MS = 65_000;

type PresenceInput = {
  lastSeen?: number | null;
  now: number;
  onlineStatus: boolean;
  lastSeenEnabled: boolean;
};

function toMillis(lastSeen?: number | null): number | null {
  if (typeof lastSeen !== 'number' || !Number.isFinite(lastSeen) || lastSeen <= 0) return null;
  return lastSeen;
}

export function isOnline(lastSeen: number | undefined | null, now: number, windowMs = ONLINE_WINDOW_MS): boolean {
  const seen = toMillis(lastSeen);
  return seen !== null && now - seen < windowMs;
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

export function describePresence({ lastSeen, now, onlineStatus, lastSeenEnabled }: PresenceInput): string {
  if (onlineStatus && isOnline(lastSeen, now)) return 'En línea';
  if (!lastSeenEnabled) return 'Desconectado';
  const label = describeLastSeen(lastSeen, now);
  return label ? `Última vez ${label}` : 'Desconectado';
}
