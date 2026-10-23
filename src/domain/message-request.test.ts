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

  it('treats our own pending request as already known', () => {
    expect(decideRequestState({ contactStatus: 'pending', hasIncoming: false, hasOutgoing: true })).toBe('none');
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
});
