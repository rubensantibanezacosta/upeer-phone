import { describe, expect, it } from 'vitest';
import { classifyConversations, requestLabel } from './incoming-requests';

const conversations = [
  { upeerId: 'aa'.repeat(32), alias: 'Ana', preview: 'Hola', timestamp: 1 },
  { upeerId: 'bb'.repeat(32), alias: '', preview: 'Solicitud', timestamp: 2 },
  { upeerId: 'cc'.repeat(32), alias: 'Carlos', preview: 'Spam', timestamp: 3 },
  { upeerId: 'DD'.repeat(32), alias: 'Dani', preview: 'Mayúsculas', timestamp: 4 },
];

const contacts = [
  { upeerId: 'aa'.repeat(32), status: 'connected' },
  { upeerId: 'cc'.repeat(32), status: 'blocked' },
  { upeerId: 'dd'.repeat(32), status: 'offline' },
];

describe('incoming message requests', () => {
  it('treats a conversation without a contact as a request', () => {
    const result = classifyConversations(conversations, contacts);
    expect(result.requests.map((item) => item.alias)).toEqual(['']);
  });

  it('keeps conversations with an accepted contact in the list', () => {
    const result = classifyConversations(conversations, contacts);
    expect(result.accepted.map((item) => item.alias)).toEqual(['Ana', 'Dani']);
  });

  it('hides conversations whose contact is blocked', () => {
    const result = classifyConversations(conversations, contacts);
    expect(result.hidden.map((item) => item.alias)).toEqual(['Carlos']);
  });

  it('matches identities regardless of case', () => {
    const result = classifyConversations(conversations, [{ upeerId: 'dd'.repeat(32), status: 'connected' }]);
    expect(result.accepted.map((item) => item.alias)).toEqual(['Dani']);
  });

  it('returns no request when every conversation belongs to a contact', () => {
    const result = classifyConversations(conversations.slice(0, 1), contacts);
    expect(result.requests).toHaveLength(0);
  });

  it('falls back to the shortened identity when there is no alias', () => {
    expect(requestLabel({ upeerId: 'bb'.repeat(32), alias: '   ' })).toBe('bbbbbbbbbbbb');
  });

  it('never treats a group as an incoming request', () => {
    const group = { upeerId: 'grp-abc', alias: 'Equipo', preview: 'Sin mensajes', timestamp: 0, group: true };
    const result = classifyConversations([...conversations, group], contacts);
    expect(result.accepted.map((item) => item.alias)).toEqual(['Ana', 'Dani', 'Equipo']);
    expect(result.requests.map((item) => item.alias)).toEqual(['']);
  });
});
