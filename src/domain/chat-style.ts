export type ChatStyleId = 'default' | 'ocean' | 'forest' | 'sunset' | 'night' | 'paper';

export type ChatStyleTint = {
  color: string;
  cx: number;
  cy: number;
  rx: number;
  ry: number;
};

export type ChatStylePreset = {
  id: ChatStyleId;
  label: string;
  description: string;
  tints: readonly ChatStyleTint[];
  stripes?: { color: string; gap: number };
};

export const CHAT_STYLE_PRESETS: readonly ChatStylePreset[] = [
  { id: 'default', label: 'Predeterminado', description: 'El fondo del tema, sin mezcla', tints: [] },
  {
    id: 'ocean',
    label: 'Océano',
    description: 'Azul y turquesa',
    tints: [
      { color: 'rgba(56, 132, 255, 0.22)', cx: 10, cy: 0, rx: 120, ry: 90 },
      { color: 'rgba(0, 198, 190, 0.18)', cx: 90, cy: 100, rx: 110, ry: 85 },
    ],
  },
  {
    id: 'forest',
    label: 'Bosque',
    description: 'Verde y oliva',
    tints: [
      { color: 'rgba(46, 170, 110, 0.20)', cx: 15, cy: 5, rx: 120, ry: 95 },
      { color: 'rgba(150, 190, 70, 0.16)', cx: 85, cy: 95, rx: 100, ry: 80 },
    ],
  },
  {
    id: 'sunset',
    label: 'Atardecer',
    description: 'Naranja y rosa',
    tints: [
      { color: 'rgba(255, 138, 64, 0.20)', cx: 12, cy: 0, rx: 125, ry: 95 },
      { color: 'rgba(228, 74, 140, 0.18)', cx: 88, cy: 100, rx: 110, ry: 85 },
    ],
  },
  {
    id: 'night',
    label: 'Noche',
    description: 'Índigo y violeta',
    tints: [
      { color: 'rgba(96, 96, 240, 0.24)', cx: 8, cy: 0, rx: 120, ry: 90 },
      { color: 'rgba(170, 80, 220, 0.20)', cx: 92, cy: 100, rx: 110, ry: 85 },
    ],
  },
  {
    id: 'paper',
    label: 'Papel',
    description: 'Rayado tenue',
    tints: [{ color: 'rgba(160, 160, 170, 0.14)', cx: 50, cy: 0, rx: 120, ry: 100 }],
    stripes: { color: 'rgba(255, 255, 255, 0.035)', gap: 12 },
  },
];

export const DEFAULT_CHAT_STYLE: ChatStyleId = 'default';

export type ChatStyleMap = Record<string, ChatStyleId>;

const PRESET_BY_ID = new Map<string, ChatStylePreset>(CHAT_STYLE_PRESETS.map((preset) => [preset.id, preset]));

export function resolveChatStyleId(value?: string | null): ChatStyleId {
  if (value && PRESET_BY_ID.has(value)) return value as ChatStyleId;
  return DEFAULT_CHAT_STYLE;
}

export function getChatStylePreset(value?: string | null): ChatStylePreset {
  return PRESET_BY_ID.get(resolveChatStyleId(value)) ?? CHAT_STYLE_PRESETS[0];
}

export function chatStyleFor(map: ChatStyleMap, chatId?: string | null): ChatStyleId {
  if (!chatId) return DEFAULT_CHAT_STYLE;
  return map[chatId] ?? DEFAULT_CHAT_STYLE;
}

export function sanitizeChatStyleMap(value: unknown): ChatStyleMap {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};

  const result: ChatStyleMap = {};
  for (const [chatId, id] of Object.entries(value as Record<string, unknown>)) {
    if (!chatId || typeof id !== 'string') continue;
    const resolved = resolveChatStyleId(id);
    if (resolved === DEFAULT_CHAT_STYLE || resolved !== id) continue;
    result[chatId] = resolved;
  }

  return result;
}

export function parseChatStyleMap(raw?: string | null): ChatStyleMap {
  if (!raw) return {};
  try {
    return sanitizeChatStyleMap(JSON.parse(raw));
  } catch {
    return {};
  }
}

export function setChatStyle(map: ChatStyleMap, chatId: string, id?: string | null): ChatStyleMap {
  if (!chatId) return map;
  const next: ChatStyleMap = { ...map };
  const resolved = resolveChatStyleId(id);
  if (resolved === DEFAULT_CHAT_STYLE) delete next[chatId];
  else next[chatId] = resolved;
  return sanitizeChatStyleMap(next);
}
