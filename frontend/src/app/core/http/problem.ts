import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail } from '../../api/models';

export interface FieldError {
  field: string;
  message: string;
}

const FALLBACK_MESSAGE = 'Algo salió mal. Intenta de nuevo.';

/** Extrae el Problem Detail (RFC 9457) que envía el backend. */
export function problemOf(error: unknown): ProblemDetail | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as ProblemDetail;
  }
  return null;
}

/** Mensaje legible para mostrar al usuario. */
export function problemMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return 'No hay conexión con el servidor.';
  }
  return problemOf(error)?.detail ?? FALLBACK_MESSAGE;
}

/** Código estable del problema (p. ej. "prediction-closed"), a partir de `type`. */
export function problemCode(error: unknown): string | null {
  const type = problemOf(error)?.type;
  return type ? type.substring(type.lastIndexOf(':') + 1) : null;
}

export function fieldErrors(error: unknown): FieldError[] {
  return (problemOf(error)?.errors ?? []).filter(
    (e): e is FieldError => typeof e.field === 'string' && typeof e.message === 'string',
  );
}
