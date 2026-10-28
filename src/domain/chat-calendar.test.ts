import { describe, expect, it } from 'vitest';

import {
  DAY_MS,
  WEEKDAY_LABELS,
  buildMonthGrid,
  cursorOf,
  dayKeyOf,
  firstMessageOfDay,
  monthLabel,
  shiftMonth,
  startOfDay,
  timestampOfDayKey,
} from './chat-calendar';

describe('startOfDay', () => {
  it('drops the time', () => {
    const start = startOfDay(new Date(2026, 9, 30, 18, 45, 12).getTime());

    expect(new Date(start).getHours()).toBe(0);
    expect(dayKeyOf(start)).toBe('2026-10-30');
  });
});

describe('dayKeyOf', () => {
  it('uses a sortable key', () => {
    expect(dayKeyOf(new Date(2026, 0, 5).getTime())).toBe('2026-01-05');
  });
});

describe('monthLabel', () => {
  it('names the month in Spanish', () => {
    expect(monthLabel({ year: 2026, month: 9 })).toBe('octubre 2026');
  });

  it('covers every month', () => {
    const labels = Array.from({ length: 12 }, (_, month) => monthLabel({ year: 2026, month }));

    expect(labels.every((label) => label.endsWith('2026'))).toBe(true);
  });
});

describe('cursorOf y shiftMonth', () => {
  it('reads the month of a timestamp', () => {
    expect(cursorOf(new Date(2026, 9, 30).getTime())).toEqual({ year: 2026, month: 9 });
  });

  it('moves between months and years', () => {
    expect(shiftMonth({ year: 2026, month: 0 }, -1)).toEqual({ year: 2025, month: 11 });
    expect(shiftMonth({ year: 2026, month: 11 }, 1)).toEqual({ year: 2027, month: 0 });
  });
});

describe('timestampOfDayKey', () => {
  it('parses a key', () => {
    expect(timestampOfDayKey('2026-10-30')).toBe(new Date(2026, 9, 30).getTime());
  });

  it('rejects anything else', () => {
    expect(timestampOfDayKey('30/10/2026')).toBeNull();
    expect(timestampOfDayKey('')).toBeNull();
  });
});

describe('buildMonthGrid', () => {
  it('pads the first weekday on Monday', () => {
    const cells = buildMonthGrid({ year: 2026, month: 9 });

    expect(cells[0]).toBeNull();
    expect(cells.filter(Boolean)).toHaveLength(31);
    expect(cells.length % 7).toBe(0);
  });

  it('starts on Monday for a month that begins on Monday', () => {
    const cells = buildMonthGrid({ year: 2026, month: 5 });

    expect(cells[0]?.day).toBe(1);
  });

  it('keeps one cell per day', () => {
    const days = buildMonthGrid({ year: 2026, month: 1 }).filter((cell) => cell !== null);

    expect(days[0]?.key).toBe('2026-02-01');
    expect(days.at(-1)?.key).toBe('2026-02-28');
  });

  it('labels the week from Monday', () => {
    expect(WEEKDAY_LABELS).toEqual(['L', 'M', 'X', 'J', 'V', 'S', 'D']);
  });
});

describe('firstMessageOfDay', () => {
  const dayStart = new Date(2026, 9, 30).getTime();
  const at = (hour: number, minute = 0) => dayStart + hour * 60 * 60 * 1000 + minute * 60 * 1000;

  it('returns the oldest message of the day from a newest first page', () => {
    const page = [
      { id: 'c', timestamp: at(18) },
      { id: 'b', timestamp: at(12) },
      { id: 'a', timestamp: at(9) },
      { id: 'prev', timestamp: dayStart - 60_000 },
    ];

    expect(firstMessageOfDay(page, dayStart)?.id).toBe('a');
  });

  it('ignores the next day', () => {
    const page = [
      { id: 'next', timestamp: at(25) },
      { id: 'b', timestamp: at(12) },
    ];

    expect(firstMessageOfDay(page, dayStart)?.id).toBe('b');
  });

  it('returns null when the day is empty', () => {
    expect(firstMessageOfDay([{ id: 'prev', timestamp: dayStart - DAY_MS }], dayStart)).toBeNull();
    expect(firstMessageOfDay([], dayStart)).toBeNull();
  });
});
