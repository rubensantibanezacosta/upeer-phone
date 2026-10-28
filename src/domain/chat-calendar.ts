export type CalendarDay = {
  key: string;
  day: number;
  month: number;
  year: number;
  timestamp: number;
};

export type MonthCursor = {
  year: number;
  month: number;
};

const MONTH_NAMES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

export const WEEKDAY_LABELS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

export const DAY_MS = 24 * 60 * 60 * 1000;

export function startOfDay(timestamp: number): number {
  const date = new Date(timestamp);
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

export function dayKeyOf(timestamp: number): string {
  const date = new Date(timestamp);
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function monthLabel({ year, month }: MonthCursor): string {
  return `${MONTH_NAMES[month]} ${year}`;
}

export function cursorOf(timestamp: number): MonthCursor {
  const date = new Date(timestamp);
  return { year: date.getFullYear(), month: date.getMonth() };
}

export function shiftMonth({ year, month }: MonthCursor, delta: number): MonthCursor {
  const shifted = new Date(year, month + delta, 1);
  return { year: shifted.getFullYear(), month: shifted.getMonth() };
}

export function timestampOfDayKey(key: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(key);
  if (!match) return null;
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])).getTime();
}

export function buildMonthGrid({ year, month }: MonthCursor): (CalendarDay | null)[] {
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
  const totalDays = new Date(year, month + 1, 0).getDate();
  const cells: (CalendarDay | null)[] = Array.from({ length: firstWeekday }, () => null);

  for (let day = 1; day <= totalDays; day += 1) {
    const date = new Date(year, month, day);
    cells.push({
      key: dayKeyOf(date.getTime()),
      day,
      month,
      year,
      timestamp: date.getTime(),
    });
  }

  while (cells.length % 7 !== 0) {
    cells.push(null);
  }

  return cells;
}

export function firstMessageOfDay<T extends { timestamp: number }>(newestFirst: readonly T[], dayStart: number): T | null {
  const dayEnd = dayStart + DAY_MS;
  let found: T | null = null;
  for (const message of newestFirst) {
    if (message.timestamp < dayStart || message.timestamp >= dayEnd) continue;
    found = message;
  }
  return found;
}
