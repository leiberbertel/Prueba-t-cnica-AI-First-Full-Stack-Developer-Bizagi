import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';

import { AuthStore } from './auth.store';
import { DeleteAccountDialog } from './delete-account.dialog';

describe('DeleteAccountDialog', () => {
  let fixture: ComponentFixture<DeleteAccountDialog>;
  let element: HTMLElement;
  let backend: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  function type(selector: string, value: string): void {
    const input = element.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submitButton(): HTMLButtonElement {
    return element.querySelector<HTMLButtonElement>('button[type=submit]')!;
  }

  beforeEach(() => {
    dialogRef.close.mockReset();
    TestBed.configureTestingModule({
      imports: [DeleteAccountDialog],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DeleteAccountDialog);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('requires the password and typing ELIMINAR before enabling the button', () => {
    expect(submitButton().disabled).toBe(true);

    type('input[type=password]', 'Secreta123');
    expect(submitButton().disabled).toBe(true);

    type('input[formcontrolname=confirmation]', 'ELIMINAR');
    expect(submitButton().disabled).toBe(false);
  });

  it.each(['eliminar', 'Eliminar', 'ELIMINA'])('rejects "%s": the word must match exactly, uppercase included', (word) => {
    type('input[type=password]', 'Secreta123');
    type('input[formcontrolname=confirmation]', word);

    expect(submitButton().disabled).toBe(true);
  });

  it('deletes the account, ends the session and closes with true', async () => {
    const endSession = vi.spyOn(TestBed.inject(AuthStore), 'endSession');
    type('input[type=password]', 'Secreta123');
    type('input[formcontrolname=confirmation]', 'ELIMINAR');

    submitButton().click();
    const request = backend.expectOne('/api/v1/me');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ password: 'Secreta123' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();

    expect(endSession).toHaveBeenCalledWith(false);
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows the server error and keeps the dialog open on a wrong password', async () => {
    type('input[type=password]', 'Incorrecta1');
    type('input[formcontrolname=confirmation]', 'ELIMINAR');

    submitButton().click();
    backend.expectOne('/api/v1/me').flush(
      { type: 'urn:polla:problem:invalid-password', detail: 'La contraseña no es correcta.' },
      { status: 403, statusText: 'Forbidden' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('[role=alert]')?.textContent).toContain('La contraseña no es correcta.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
