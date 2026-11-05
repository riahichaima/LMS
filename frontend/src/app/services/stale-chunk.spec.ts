import { isStaleChunkError } from './stale-chunk';

describe('isStaleChunkError', () => {
  it('reconnaît un fichier de page disparu après une mise en ligne (Chrome, Firefox, Safari)', () => {
    expect(isStaleChunkError(new TypeError('Failed to fetch dynamically imported module: https://lms/chunk-AB12CD34.js'))).toBe(true);
    expect(isStaleChunkError(new TypeError('error loading dynamically imported module'))).toBe(true);
    expect(isStaleChunkError(new TypeError('Importing a module script failed.'))).toBe(true);
  });

  it('ignore les autres erreurs de navigation', () => {
    expect(isStaleChunkError(new Error('Cannot match any routes'))).toBe(false);
    expect(isStaleChunkError(null)).toBe(false);
  });
});
