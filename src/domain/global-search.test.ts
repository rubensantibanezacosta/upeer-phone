import { describe, expect, it } from 'vitest';

import { CONTACT_RESULT_LIMIT, MESSAGE_RESULT_LIMIT, buildSearchSections, normalizeSearchText, searchMessageText, visibleSearchSections, type SearchData } from './global-search';

const chat = (overrides: Partial<SearchData['chats'][number]> & { upeerId: string; title: string }) => ({
  preview: 'Sin mensajes',
  unread: 0,
  timestamp: 1,
  ...overrides,
});

const contact = (overrides: Partial<SearchData['contacts'][number]> & { upeerId: string; name: string }) => ({
  address: '200:1234::1',
  ...overrides,
});

const baseData = (): SearchData => ({
  chats: [
    chat({ upeerId: 'a1', title: 'Ana Pérez', timestamp: 30 }),
    chat({ upeerId: 'b2', title: 'Bruno', timestamp: 20, unread: 2 }),
    chat({ upeerId: 'g1', title: 'Grupo Vecinos', timestamp: 10, group: true }),
  ],
  contacts: [
    contact({ upeerId: 'a1', name: 'Ana Pérez' }),
    contact({ upeerId: 'c3', name: 'Carla Gómez' }),
    contact({ upeerId: 'd4', name: 'Carlos Ruiz', blocked: true }),
  ],
  groups: [],
  messages: [],
});

const sectionIds = (data: SearchData, query: string, chip: Parameters<typeof buildSearchSections>[1] = 'all') =>
  buildSearchSections(query, chip, data).map((section) => section.id);

describe('normalizeSearchText', () => {
  it('quita diacríticos y baja a minúsculas', () => {
    expect(normalizeSearchText('Ana PÉREZ')).toBe('ana perez');
  });
});

describe('searchMessageText', () => {
  it('descarta adjuntos serializados', () => {
    expect(searchMessageText('{"kind":"file","name":"a.pdf"}')).toBeNull();
  });

  it('quita el prefijo de los eventos de sistema', () => {
    expect(searchMessageText('__SYS__|Ivan creó el grupo')).toBe('Ivan creó el grupo');
  });
});

describe('buildSearchSections', () => {
  it('no devuelve secciones sin término', () => {
    expect(buildSearchSections('   ', 'all', baseData())).toEqual([]);
  });

  it('ordena las secciones siempre igual', () => {
    expect(sectionIds(baseData(), 'a')).toEqual(['chats', 'contacts', 'groups', 'messages']);
  });

  it('encuentra chats por título sin acentos y permite prefijo de identidad', () => {
    const data = baseData();
    expect(buildSearchSections('perez', 'all', data)[0].rows.map((row) => row.key)).toEqual(['chat:a1']);
    expect(buildSearchSections('b2', 'all', data)[0].rows.map((row) => row.key)).toEqual(['chat:b2']);
  });

  it('pone los fijados primero y luego por recencia', () => {
    const data = baseData();
    data.chats = [chat({ upeerId: 'z1', title: 'Zoe', timestamp: 5, pinned: true }), chat({ upeerId: 'z2', title: 'Zoe dos', timestamp: 90 })];
    expect(buildSearchSections('zoe', 'all', data)[0].rows.map((row) => row.key)).toEqual(['chat:z1', 'chat:z2']);
  });

  it('no repite en Contactos lo que ya sale en Chats', () => {
    const sections = buildSearchSections('a', 'all', baseData());
    const chats = sections[0].rows.map((row) => row.key);
    const contacts = sections[1].rows.map((row) => row.key);
    expect(chats).toContain('chat:a1');
    expect(contacts).not.toContain('contact:a1');
    expect(contacts).toContain('contact:c3');
  });

  it('excluye contactos bloqueados', () => {
    const sections = buildSearchSections('carlos', 'all', baseData());
    expect(sections[1].rows).toEqual([]);
  });

  it('ordena contactos por coincidencia exacta, prefijo y alfabeto', () => {
    const data = baseData();
    data.chats = [];
    data.contacts = [contact({ upeerId: 'x1', name: 'Marta' }), contact({ upeerId: 'x2', name: 'Mar' }), contact({ upeerId: 'x3', name: 'Mar' })];
    expect(buildSearchSections('mar', 'all', data)[1].rows.map((row) => row.key)).toEqual(['contact:x2', 'contact:x3', 'contact:x1']);
  });

  it('recorta contactos al tope pero cuenta todos', () => {
    const data = baseData();
    data.chats = [];
    data.contacts = Array.from({ length: CONTACT_RESULT_LIMIT + 2 }, (_, index) => contact({ upeerId: `k${index}`, name: `Contacto ${index}` }));
    const section = buildSearchSections('contacto', 'all', data)[1];
    expect(section.rows).toHaveLength(CONTACT_RESULT_LIMIT);
    expect(section.total).toBe(CONTACT_RESULT_LIMIT + 2);
  });

  it('oculta Contactos con el chip de grupos', () => {
    expect(sectionIds(baseData(), 'a', 'groups')).not.toContain('contacts');
  });

  it('aplica el chip a cada sujeto', () => {
    const unread = buildSearchSections('a', 'unread', baseData());
    expect(unread.find((section) => section.id === 'contacts')?.rows).toEqual([]);
    const favorites = buildSearchSections('a', 'favorites', baseData());
    expect(favorites.find((section) => section.id === 'chats')?.rows).toEqual([]);
  });

  it('no fuerza la sección de grupos con el chip de grupos', () => {
    expect(buildSearchSections('vecinos', 'groups', baseData()).find((section) => section.id === 'groups')?.rows).toEqual([]);
  });

  it('lista grupos propios y grupos compartidos con el contacto que coincide', () => {
    const data = baseData();
    data.groups = [
      { groupId: 'g1', name: 'Grupo Vecinos', members: ['a1', 'g1'], updatedAt: 5 },
      { groupId: 'g9', name: 'Peña', members: ['c3', 'g9'], updatedAt: 9 },
    ];
    const groups = buildSearchSections('carla', 'all', data)[2];
    expect(groups.rows.map((row) => row.key)).toEqual(['group:g9']);
    expect(groups.rows[0].subtitle).toBe('Compartido con Carla Gómez');
  });

  it('no repite grupos que ya salen en Chats', () => {
    const data = baseData();
    data.groups = [{ groupId: 'g1', name: 'Grupo Vecinos', members: ['g1'], updatedAt: 5 }];
    expect(buildSearchSections('vecinos', 'all', data)[2].rows).toEqual([]);
  });

  it('busca en mensajes, descarta adjuntos y ordena por fecha descendente', () => {
    const data = baseData();
    data.messages = [
      { id: 'm1', chatUpeerId: 'a1', chatTitle: 'Ana Pérez', content: 'hola mundo', timestamp: 10, isMine: false },
      { id: 'm2', chatUpeerId: 'a1', chatTitle: 'Ana Pérez', content: 'otra cosa', timestamp: 40, isMine: true },
      { id: 'm3', chatUpeerId: 'a1', chatTitle: 'Ana Pérez', content: '{"kind":"file","name":"mundo.pdf"}', timestamp: 50, isMine: false },
    ];
    const messages = buildSearchSections('mundo', 'all', data)[3];
    expect(messages.rows.map((row) => row.key)).toEqual(['message:a1:m1']);
    expect(messages.rows[0].target).toEqual({ kind: 'chat', upeerId: 'a1', messageId: 'm1' });
  });

  it('recorta mensajes al tope pero cuenta todos', () => {
    const data = baseData();
    data.messages = Array.from({ length: MESSAGE_RESULT_LIMIT + 3 }, (_, index) => ({
      id: `m${index}`,
      chatUpeerId: 'a1',
      chatTitle: 'Ana Pérez',
      content: `nota ${index}`,
      timestamp: index,
      isMine: false,
    }));
    const section = buildSearchSections('nota', 'all', data)[3];
    expect(section.rows).toHaveLength(MESSAGE_RESULT_LIMIT);
    expect(section.total).toBe(MESSAGE_RESULT_LIMIT + 3);
  });

  it('con el chip de no leídos solo lista mensajes de chats con no leídos', () => {
    const data = baseData();
    data.messages = [
      { id: 'm1', chatUpeerId: 'a1', chatTitle: 'Ana Pérez', content: 'nota uno', timestamp: 1, isMine: false },
      { id: 'm2', chatUpeerId: 'b2', chatTitle: 'Bruno', content: 'nota dos', timestamp: 2, isMine: false },
    ];
    expect(buildSearchSections('nota', 'unread', data)[3].rows.map((row) => row.key)).toEqual(['message:b2:m2']);
  });
});

describe('visibleSearchSections', () => {
  it('descarta secciones sin filas', () => {
    const data = baseData();
    const sections = buildSearchSections('vecinos', 'all', data);
    expect(visibleSearchSections(sections).map((section) => section.id)).toEqual(['chats']);
  });
});
