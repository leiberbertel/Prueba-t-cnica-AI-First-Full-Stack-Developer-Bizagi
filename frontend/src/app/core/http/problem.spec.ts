import { HttpErrorResponse } from '@angular/common/http';

import { problemCode, problemMessage } from './problem';

describe('problem', () => {
  const body = { type: 'urn:polla:problem:invalid-password', detail: 'La contraseña no es correcta.' };

  it('reads a Problem Detail received as an object', () => {
    const error = new HttpErrorResponse({ status: 403, error: body });

    expect(problemMessage(error)).toBe('La contraseña no es correcta.');
    expect(problemCode(error)).toBe('invalid-password');
  });

  it('reads a Problem Detail received as text (operations without response body)', () => {
    const error = new HttpErrorResponse({ status: 403, error: JSON.stringify(body) });

    expect(problemMessage(error)).toBe('La contraseña no es correcta.');
    expect(problemCode(error)).toBe('invalid-password');
  });

  it('falls back to a generic message for non-JSON errors', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' })))
      .toBe('Algo salió mal. Intenta de nuevo.');
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toBe('No hay conexión con el servidor.');
  });
});
