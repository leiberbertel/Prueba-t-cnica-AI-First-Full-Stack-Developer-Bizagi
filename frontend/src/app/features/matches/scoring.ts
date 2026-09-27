/** Etiquetas de puntuación para la UI (la regla real vive en el backend: ScoringPolicy). */
export interface PointsBadge {
  label: string;
  tone: 'exact' | 'outcome' | 'miss';
}

export function pointsBadge(points: number | null | undefined): PointsBadge | null {
  if (points === null || points === undefined) {
    return null;
  }
  if (points >= 3) {
    return { label: `+${points} Exacto`, tone: 'exact' };
  }
  if (points > 0) {
    return { label: `+${points} Ganador`, tone: 'outcome' };
  }
  return { label: '0 Fallo', tone: 'miss' };
}
