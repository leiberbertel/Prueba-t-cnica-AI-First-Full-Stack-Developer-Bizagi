import { ChangeDetectionStrategy, Component, booleanAttribute, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../core/auth/auth.store';
import { problemMessage } from '../../core/http/problem';
import { Icon } from '../../core/ui/icon';
import { AuthLayout } from './auth-layout';

@Component({
  selector: 'app-login-page',
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
    <app-auth-layout heading="Bienvenido de vuelta" subtitle="Inicia sesión para registrar tus predicciones.">
      @if (accountDeleted() && !error()) {
        <p class="info" role="status"><app-icon name="check_circle" /> Tu cuenta y tus datos fueron eliminados.</p>
      }
      @if (error()) {
        <p class="error" role="alert"><app-icon name="error" /> {{ error() }}</p>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
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
          <input matInput [type]="showPassword() ? 'text' : 'password'" formControlName="password" autocomplete="current-password" />
          <button mat-icon-button matSuffix type="button" (click)="showPassword.set(!showPassword())"
                  [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
            <app-icon [name]="showPassword() ? 'visibility_off' : 'visibility'" />
          </button>
          @if (form.controls.password.hasError('required')) {
            <mat-error>La contraseña es obligatoria</mat-error>
          }
        </mat-form-field>
        <button mat-flat-button class="submit" type="submit" [disabled]="loading()">
          @if (loading()) {
            <mat-spinner diameter="22" />
          } @else {
            Iniciar sesión
          }
        </button>
      </form>
      <p class="switch">¿No tienes cuenta? <a routerLink="/registro">Regístrate</a></p>
    </app-auth-layout>
  `,
  styleUrl: './auth-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Ruta a la que volver tras el login (query param, vía withComponentInputBinding). */
  readonly returnUrl = input<string>();
  /** Query param tras eliminar la cuenta (HU-01.6). */
  readonly cuentaEliminada = input(false, { transform: booleanAttribute });
  protected readonly accountDeleted = this.cuentaEliminada;

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.form.getRawValue());
      await this.router.navigateByUrl(this.safeReturnUrl());
    } catch (error) {
      this.error.set(problemMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  /** Solo rutas internas: evita redirecciones abiertas a otros dominios. */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') ? url : '/partidos';
  }
}
