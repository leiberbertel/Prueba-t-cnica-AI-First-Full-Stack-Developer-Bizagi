import { timeUntil } from '../../core/time/clock';
import { pointsBadge } from './scoring';

describe('pointsBadge', () => {
  it.each([
    [3, '+3 Exacto', 'exact'],
    [1, '+1 Ganador', 'outcome'],
    [0, '0 Fallo', 'miss'],
  ])('maps %i points to "%s"', (points, label, tone) => {
    expect(pointsBadge(points)).toEqual({ label, tone });
  });

  it('returns null while the match has no result', () => {
    expect(pointsBadge(null)).toBeNull();
    expect(pointsBadge(undefined)).toBeNull();
  });
});

describe('timeUntil', () => {
  const now = Date.UTC(2026, 10, 1, 12, 0);

  it.each([
    [15 * 60_000, 'en 15 min'],
    [5 * 3_600_000, 'en 5 h'],
    [3 * 86_400_000, 'en 3 días'],
    [-60_000, 'en 0 min'],
  ])('formats a delta of %i ms as "%s"', (delta, expected) => {
    expect(timeUntil(now + delta, now)).toBe(expected);
  });
});
