export type AppearanceTheme = 'system' | 'light' | 'dark';
export type AppearanceFontSize = 'small' | 'medium' | 'large';

export type Preferences = {
  theme: AppearanceTheme;
  fontSize: AppearanceFontSize;
  readReceipts: boolean;
  onlineStatus: boolean;
  lastSeen: boolean;
  messageNotifications: boolean;
  requestNotifications: boolean;
  notificationSound: boolean;
};

export const DEFAULT_PREFERENCES: Preferences = {
  theme: 'system',
  fontSize: 'medium',
  readReceipts: true,
  onlineStatus: true,
  lastSeen: true,
  messageNotifications: true,
  requestNotifications: true,
  notificationSound: true,
};

const THEMES: AppearanceTheme[] = ['system', 'light', 'dark'];
const FONT_SIZES: AppearanceFontSize[] = ['small', 'medium', 'large'];

function readBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

export function parsePreferences(raw: string | null): Preferences {
  if (!raw) return { ...DEFAULT_PREFERENCES };
  try {
    const parsed = JSON.parse(raw) as Partial<Preferences>;
    return {
      theme: THEMES.includes(parsed.theme as AppearanceTheme) ? (parsed.theme as AppearanceTheme) : DEFAULT_PREFERENCES.theme,
      fontSize: FONT_SIZES.includes(parsed.fontSize as AppearanceFontSize) ? (parsed.fontSize as AppearanceFontSize) : DEFAULT_PREFERENCES.fontSize,
      readReceipts: readBoolean(parsed.readReceipts, DEFAULT_PREFERENCES.readReceipts),
      onlineStatus: readBoolean(parsed.onlineStatus, DEFAULT_PREFERENCES.onlineStatus),
      lastSeen: readBoolean(parsed.lastSeen, DEFAULT_PREFERENCES.lastSeen),
      messageNotifications: readBoolean(parsed.messageNotifications, DEFAULT_PREFERENCES.messageNotifications),
      requestNotifications: readBoolean(parsed.requestNotifications, DEFAULT_PREFERENCES.requestNotifications),
      notificationSound: readBoolean(parsed.notificationSound, DEFAULT_PREFERENCES.notificationSound),
    };
  } catch {
    console.warn('Las preferencias guardadas no se pudieron leer; se usan las de por defecto');
    return { ...DEFAULT_PREFERENCES };
  }
}
