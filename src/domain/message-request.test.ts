import { describe, expect, it } from 'vitest';
import { decideRequestState } from './message-request';

describe('message request decision', () => {
  it('asks for confirmation when the contact arrived as an incoming request', () => {
    expect(decideRequestState({ contactStatus: 'incoming', hasIncoming: true, hasOutgoing: false })).toBe('request');
  });

  it('never asks when the contact is already accepted', () => {
    expect(decideRequestState({ contactStatus: 'connected', hasIncoming: true, hasOutgoing: false })).toBe('none');
    expect(decideRequestState({ contactStatus: 'offline', hasIncoming: true, hasOutgoing: false })).toBe('none');
    expect(decideRequestState({ contactStatus: 'blocked', hasIncoming: true, hasOutgoing: false })).toBe('none');
  });

  it('never asks for confirmation while our own request is still pending', () => {
    expect(decideRequestState({ contactStatus: 'pending', hasIncoming: false, hasOutgoing: false })).toBe('none');
  });

  it('promotes a pending contact once the conversation has messages', () => {
    expect(decideRequestState({ contactStatus: 'pending', hasOutgoing: true, hasIncoming: false })).toBe('auto-accept');
    expect(decideRequestState({ contactStatus: 'pending', hasOutgoing: false, hasIncoming: true })).toBe('auto-accept');
    expect(decideRequestState({ contactStatus: 'pending', hasOutgoing: true, hasIncoming: true })).toBe('auto-accept');
  });

  it('asks when an unknown peer writes for the first time', () => {
    expect(decideRequestState({ hasIncoming: true, hasOutgoing: false })).toBe('request');
  });

  it('auto-accepts when the conversation already existed', () => {
    expect(decideRequestState({ hasIncoming: true, hasOutgoing: true })).toBe('auto-accept');
    expect(decideRequestState({ hasIncoming: false, hasOutgoing: true })).toBe('auto-accept');
  });

  it('stays quiet in an empty conversation', () => {
    expect(decideRequestState({ hasIncoming: false, hasOutgoing: false })).toBe('none');
  });

  it('never treats a group as a message request', () => {
    expect(decideRequestState({ hasIncoming: true, hasOutgoing: false, isGroup: true })).toBe('none');
    expect(decideRequestState({ contactStatus: 'incoming', hasIncoming: true, hasOutgoing: false, isGroup: true })).toBe('none');
  });
});
