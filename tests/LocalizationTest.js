// Basic tests for localization helper
const { pickLocale } = require('../src/middleware/Localization');
describe('Localization', () => {
  test('defaults to en', () => {
    expect(pickLocale(null)).toBe('en');
  });
  test('prefers de when present', () => {
    expect(pickLocale('de, en;q=0.9')).toBe('de');
  });
});
