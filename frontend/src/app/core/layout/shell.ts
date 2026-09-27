import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthStore } from '../auth/auth.store';
import { Icon } from '../ui/icon';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatMenuModule, Icon],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly auth = inject(AuthStore);

  protected readonly navItems = computed<NavItem[]>(() => [
    { path: '/partidos', label: 'Partidos', icon: 'sports_soccer' },
    { path: '/ranking', label: 'Ranking', icon: 'leaderboard' },
    ...(this.auth.isAdmin() ? [{ path: '/admin', label: 'Resultados', icon: 'admin_panel_settings' }] : []),
  ]);

  protected readonly initials = computed(() =>
    (this.auth.user()?.displayName ?? '?')
      .split(' ')
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase(),
  );

  protected logout(): void {
    void this.auth.logout();
  }
}
