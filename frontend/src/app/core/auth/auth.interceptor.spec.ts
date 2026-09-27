import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';

const USER = { id: 1, email: 'ana@example.com', displayName: 'Ana', role: 'USER' as const };

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthStore;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);

    // Sesión inicial con el token "old"
    const login = auth.login({ email: USER.email, password: 'Secreta123' });
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 'old', expiresIn: 900, user: USER });
    await login;
  });

  afterEach(() => backend.verify());

  it('adds the bearer token to API requests', () => {
    http.get('/api/v1/matches').subscribe();

    const request = backend.expectOne('/api/v1/matches');
    expect(request.request.headers.get('Authorization')).toBe('Bearer old');
    request.flush([]);
  });

  it('does not add the token to auth endpoints', () => {
    http.post('/api/v1/auth/refresh', {}).subscribe({ error: () => undefined });

    const request = backend.expectOne('/api/v1/auth/refresh');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('refreshes once on 401 and retries concurrent requests with the new token', async () => {
    const first = firstValueFrom(http.get('/api/v1/matches'));
    const second = firstValueFrom(http.get('/api/v1/leaderboard'));

    backend.expectOne('/api/v1/matches').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/leaderboard').flush(null, { status: 401, statusText: 'Unauthorized' });

    // Un solo refresh compartido por ambas peticiones
    backend.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'new', expiresIn: 900, user: USER });

    const retriedMatches = backend.expectOne('/api/v1/matches');
    const retriedLeaderboard = backend.expectOne('/api/v1/leaderboard');
    expect(retriedMatches.request.headers.get('Authorization')).toBe('Bearer new');
    expect(retriedLeaderboard.request.headers.get('Authorization')).toBe('Bearer new');
    retriedMatches.flush(['ok']);
    retriedLeaderboard.flush(['ok']);

    await expect(first).resolves.toEqual(['ok']);
    await expect(second).resolves.toEqual(['ok']);
  });

  it('ends the session and goes to login when refresh fails', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const request = firstValueFrom(http.get('/api/v1/matches'));

    backend.expectOne('/api/v1/matches').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(request).rejects.toBeTruthy();
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
