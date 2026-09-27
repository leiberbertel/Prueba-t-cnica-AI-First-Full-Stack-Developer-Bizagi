import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { fieldErrors, problemMessage } from '../../core/http/problem';
import { Icon } from '../../core/ui/icon';
import { AuthLayout } from './auth-layout';

/** Mismas reglas que el backend (CA-01.3, CA-01.4). */
const PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d).+$/u;

@Component({
  selector: 'app-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AuthLayout,
    Icon,
  ],
  template: `
    <app-auth-layout heading="Únete a la polla" subtitle="Crea tu cuenta y empieza a predecir.">
      @if (error()) {
        <p class="error" role="alert"><app-icon name="error" /> {{ error() }}</p>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Nombre visible</mat-label>
          <input matInput formControlName="displayName" autocomplete="nickname" maxlength="40" />
          <mat-hint>Así aparecerás en el ranking</mat-hint>
          @if (form.controls.displayName.hasError('required')) {
            <mat-error>El nombre es obligatorio</mat-error>
          } @else if (form.controls.displayName.hasError('minlength')) {
            <mat-error>Mínimo 2 caracteres</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Email</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="email" />
          @if (form.controls.email.hasError('required')) {
            <mat-error>El email es obligatorio</mat-error>
          } @else if (form.controls.email.hasError('email')) {
            <mat-error>Email inválido</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Contraseña</mat-label>
          <input matInput type="password" formControlName="password" autocomplete="new-password" maxlength="72" />
          <mat-hint>Mínimo 8 caracteres, con al menos una letra y un número</mat-hint>
          @if (form.controls.password.hasError('required')) {
            <mat-error>La contraseña es obligatoria</mat-error>
          } @else if (form.controls.password.hasError('minlength')) {
            <mat-error>Mínimo 8 caracteres</mat-error>
          } @else if (form.controls.password.hasError('pattern')) {
            <mat-error>Debe tener al menos una letra y un número</mat-error>
          }
        </mat-form-field>
        <button mat-flat-button class="submit" type="submit" [disabled]="loading()">
          @if (loading()) {
            <mat-spinner diameter="22" />
          } @else {
            Crear cuenta
          }
        </button>
      </form>
      <p class="switch">¿Ya tienes cuenta? <a routerLink="/login">Inicia sesión</a></p>
    </app-auth-layout>
  `,
  styleUrl: './auth-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(40)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72),
      Validators.pattern(PASSWORD_PATTERN)]],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.auth.register(this.form.getRawValue());
      await this.router.navigate(['/partidos']);
    } catch (error) {
      const errors = fieldErrors(error);
      this.error.set(errors.length ? errors.map((e) => e.message).join('. ') : problemMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
}
