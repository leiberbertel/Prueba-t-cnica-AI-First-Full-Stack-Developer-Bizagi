import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, firstValueFrom, map, shareReplay, tap } from 'rxjs';

import { Api } from '../../api/api';
import { login, logout, refresh, register } from '../../api/functions';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../../api/models';

interface Session {
  user: User;
  accessToken: string;
}

/**
 * Estado de autenticación con signals (ADR-0004):
 * - El access token vive SOLO en memoria (nunca en localStorage).
 * - La sesión se restaura al cargar la app con la cookie HttpOnly de refresh.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.user()?.role === 'ADMIN');
  readonly accessToken = computed(() => this.session()?.accessToken ?? null);

  login(credentials: LoginRequest): Promise<User> {
    return this.authenticate(this.api.invoke(login, { body: credentials }));
  }

  register(data: RegisterRequest): Promise<User> {
    return this.authenticate(this.api.invoke(register, { body: data }));
  }

  /** Renueva el access token. Llamadas concurrentes comparten una sola petición en vuelo. */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.api.invoke(refresh).pipe(
      tap((response) => this.start(response)),
      map((response) => response.accessToken),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshInFlight;
  }

  /** Se ejecuta al iniciar la app: si hay cookie de refresh válida, la sesión se restaura. */
  async restoreSession(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.session.set(null);
    }
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.invoke(logout));
    } finally {
      this.endSession();
    }
  }

  /** Sesión expirada o revocada: limpia el estado y lleva al login. */
  endSession(redirect = true): void {
    this.session.set(null);
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  private async authenticate(request: Observable<AuthResponse>): Promise<User> {
    const response = await firstValueFrom(request);
    this.start(response);
    return response.user;
  }

  private start(response: AuthResponse): void {
    this.session.set({ user: response.user, accessToken: response.accessToken });
  }
}
