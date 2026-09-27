import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { problemMessage } from '../http/problem';
import { Icon } from '../ui/icon';
import { AuthStore } from './auth.store';

export const CONFIRMATION_WORD = 'ELIMINAR';

/**
 * Eliminar mi cuenta (HU-01.6): explica qué se borra, pide la contraseña (re-autenticación) y escribir
 * {@link CONFIRMATION_WORD}. Cierra con {@code true} si la cuenta se eliminó.
 */
@Component({
  selector: 'app-delete-account-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    Icon,
  ],
  template: `
    <h2 mat-dialog-title>Eliminar mi cuenta</h2>
    <mat-dialog-content>
      <p class="warning"><app-icon name="warning" /> Esta acción es definitiva y no se puede deshacer.</p>
      <p>Se eliminarán:</p>
      <ul>
        <li>Tu cuenta, tu nombre y tu email.</li>
        <li>Todas tus predicciones y tus puntos.</li>
        <li>Tu lugar en el ranking.</li>
      </ul>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
      <form id="delete-account-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Tu contraseña</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="current-password" />
          @if (form.controls.password.hasError('required')) {
            <mat-error>Confirma con tu contraseña</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Escribe {{ confirmationWord }} (en mayúsculas) para confirmar</mat-label>
          <input matInput formControlName="confirmation" autocomplete="off" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="loading()">Cancelar</button>
      <button mat-flat-button class="danger" type="submit" form="delete-account-form" [disabled]="!canSubmit()">
        @if (loading()) {
          <mat-spinner diameter="20" />
        } @else {
          Eliminar definitivamente
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    form { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
    mat-form-field { width: 100%; }
    ul { margin: 4px 0 12px; padding-left: 20px; }
    .warning { display: flex; align-items: center; gap: 8px; font-weight: 600; color: var(--mat-sys-error); }
    .error {
      padding: 10px 12px; border-radius: 10px;
      background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container);
    }
    .danger {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeleteAccountDialog {
  private readonly auth = inject(AuthStore);
  private readonly dialogRef = inject(MatDialogRef<DeleteAccountDialog, boolean>);

  protected readonly confirmationWord = CONFIRMATION_WORD;
  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', Validators.required],
    confirmation: [''],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly formValue = signal(this.form.getRawValue());

  constructor() {
    this.form.valueChanges.subscribe(() => this.formValue.set(this.form.getRawValue()));
  }

  protected readonly canSubmit = computed(() => {
    const { password, confirmation } = this.formValue();
    // Coincidencia exacta (mayúsculas incluidas): escribirla a propósito es la confirmación.
    return !this.loading() && password.length > 0 && confirmation.trim() === CONFIRMATION_WORD;
  });

  protected async submit(): Promise<void> {
    if (!this.canSubmit()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    try {
      await this.auth.deleteAccount(this.form.getRawValue().password);
      this.dialogRef.close(true);
    } catch (error) {
      this.error.set(problemMessage(error));
      this.dialogRef.disableClose = false;
    } finally {
      this.loading.set(false);
    }
  }
}
