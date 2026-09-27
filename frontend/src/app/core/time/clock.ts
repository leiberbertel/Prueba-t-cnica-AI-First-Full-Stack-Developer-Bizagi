import { Injectable, signal } from '@angular/core';

/**
 * Hora actual como signal, actualizada cada 30 s. Solo para la UX (cuentas regresivas);
 * la regla de cierre la valida el servidor.
 */
@Injectable({ providedIn: 'root' })
export class Clock {
  private readonly current = signal(Date.now());
  readonly now = this.current.asReadonly();

  constructor() {
    setInterval(() => this.current.set(Date.now()), 30_000);
  }
}

/** "en 3 días", "en 5 h", "en 12 min". */
export function timeUntil(target: number, now: number): string {
  const minutes = Math.max(0, Math.round((target - now) / 60_000));
  if (minutes < 60) {
    return `en ${minutes} min`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 48) {
    return `en ${hours} h`;
  }
  return `en ${Math.round(hours / 24)} días`;
}
