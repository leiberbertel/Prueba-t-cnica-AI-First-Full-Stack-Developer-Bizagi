import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { Api } from '../../api/api';
import { getUserPredictionHistory } from '../../api/functions';
import { AuthStore } from '../../core/auth/auth.store';
import { problemMessage } from '../../core/http/problem';
import { Icon } from '../../core/ui/icon';
import { TeamLabel } from '../../core/ui/team-label';
import { pointsBadge } from '../matches/scoring';

@Component({
  selector: 'app-history-page',
  imports: [DatePipe, RouterLink, MatProgressBarModule, Icon, TeamLabel],
  templateUrl: './history.page.html',
  styleUrl: './history.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryPage {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthStore);

  /** Parámetro de ruta :userId (withComponentInputBinding). */
  readonly userId = input.required({ transform: numberAttribute });

  protected readonly history = rxResource({
    params: () => this.userId(),
    stream: ({ params }) => this.api.invoke(getUserPredictionHistory, { userId: params }),
  });
  protected readonly isMine = computed(() => this.userId() === this.auth.user()?.id);
  protected readonly totalPoints = computed(() =>
    (this.history.value()?.items ?? []).reduce((sum, item) => sum + (item.points ?? 0), 0),
  );
  protected readonly errorMessage = computed(() => {
    const error = this.history.error();
    return error ? problemMessage(error) : null;
  });

  protected readonly badge = pointsBadge;
}
