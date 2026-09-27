import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';

import { Api } from '../../api/api';
import { getLeaderboard } from '../../api/functions';
import { LeaderboardEntry } from '../../api/models';
import { AuthStore } from '../../core/auth/auth.store';
import { problemMessage } from '../../core/http/problem';
import { Icon } from '../../core/ui/icon';

@Component({
  selector: 'app-leaderboard-page',
  imports: [RouterLink, MatProgressBarModule, Icon],
  templateUrl: './leaderboard.page.html',
  styleUrl: './leaderboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LeaderboardPage {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);

  protected readonly leaderboard = rxResource({ stream: () => this.api.invoke(getLeaderboard) });
  protected readonly entries = computed(() => this.leaderboard.value() ?? []);
  /** Podio: primeros tres puestos, solo si ya hay puntos en juego. */
  protected readonly podium = computed(() => {
    const top = this.entries().slice(0, 3);
    return top.some((entry) => entry.points > 0) ? top : [];
  });
  protected readonly errorMessage = computed(() => {
    const error = this.leaderboard.error();
    return error ? problemMessage(error) : null;
  });

  protected isMe(entry: LeaderboardEntry): boolean {
    return entry.userId === this.auth.user()?.id;
  }

  protected open(entry: LeaderboardEntry): void {
    void this.router.navigate(['/ranking', entry.userId]);
  }
}
