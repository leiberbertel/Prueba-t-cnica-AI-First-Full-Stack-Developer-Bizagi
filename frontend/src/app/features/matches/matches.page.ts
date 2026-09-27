import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { EMPTY } from 'rxjs';

import { Api } from '../../api/api';
import { getMySummary, listMatches } from '../../api/functions';
import { Match, Prediction } from '../../api/models';
import { AuthStore } from '../../core/auth/auth.store';
import { problemMessage } from '../../core/http/problem';
import { Icon } from '../../core/ui/icon';
import { MatchCard } from './match-card';

type Filter = 'all' | 'pending' | 'A' | 'B';

@Component({
  selector: 'app-matches-page',
  imports: [RouterLink, MatButtonToggleModule, MatProgressBarModule, Icon, MatchCard],
  templateUrl: './matches.page.html',
  styleUrl: './matches.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchesPage {
  private readonly api = inject(Api);
  protected readonly auth = inject(AuthStore);

  protected readonly matches = rxResource({ stream: () => this.api.invoke(listMatches) });
  protected readonly summary = rxResource({
    params: () => !this.auth.isAdmin() || undefined,
    stream: ({ params }) => (params ? this.api.invoke(getMySummary) : EMPTY),
  });

  protected readonly filter = signal<Filter>('all');
  protected readonly errorMessage = computed(() => {
    const error = this.matches.error();
    return error ? problemMessage(error) : null;
  });

  /** Partidos del filtro activo, en orden cronológico (cada tarjeta muestra su fecha). */
  protected readonly visibleMatches = computed<Match[]>(() => {
    const filter = this.filter();
    return (this.matches.value() ?? []).filter((match) => {
      switch (filter) {
        case 'pending':
          return match.predictionOpen && !match.myPrediction;
        case 'A':
        case 'B':
          return match.group === filter;
        default:
          return true;
      }
    });
  });

  protected onSaved(saved: Prediction): void {
    this.matches.value.update((matches) =>
      matches?.map((match) => (match.id === saved.matchId ? { ...match, myPrediction: saved } : match)),
    );
    this.summary.reload();
  }
}
