import { describe, expect, it } from 'vitest';
import { isPlaceholderName, placeholderName, resolveContactName } from './contact-name';

const ID = 'da4163f3528710db1adeca2211274d217728845044c70b912759e856493d1834';

describe('contact name resolution', () => {
  it('treats the identity prefix as a placeholder', () => {
    expect(isPlaceholderName('da4163f35287', ID)).toBe(true);
    expect(isPlaceholderName('  DA4163F35287  ', ID)).toBe(true);
    expect(isPlaceholderName('', ID)).toBe(true);
    expect(isPlaceholderName(undefined, ID)).toBe(true);
    expect(isPlaceholderName('Rubén Casa', ID)).toBe(false);
  });

  it('keeps a real stored name over the remote alias', () => {
    expect(resolveContactName('Rubén Casa', 'Rubén', ID)).toBe('Rubén Casa');
  });

  it('prefers the remote alias when the stored name is the placeholder', () => {
    expect(resolveContactName('da4163f35287', 'Rubén Casa', ID)).toBe('Rubén Casa');
    expect(resolveContactName('', 'Rubén Casa', ID)).toBe('Rubén Casa');
    expect(resolveContactName(undefined, 'Rubén Casa', ID)).toBe('Rubén Casa');
  });

  it('falls back to the identity prefix when nothing else exists', () => {
    expect(resolveContactName(undefined, undefined, ID)).toBe('da4163f35287');
    expect(resolveContactName('da4163f35287', '   ', ID)).toBe(placeholderName(ID));
  });
});
