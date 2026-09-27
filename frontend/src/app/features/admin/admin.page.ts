import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { Api } from '../../api/api';
import { listMatchesForAdmin, registerResult } from '../../api/functions';
import { AdminMatch, Score } from '../../api/models';
import { problemCode, problemMessage } from '../../core/http/problem';
import { Clock } from '../../core/time/clock';
import { Icon } from '../../core/ui/icon';
import { TeamLabel } from '../../core/ui/team-label';
import { ScoreStepper } from '../matches/score-stepper';
import { ConfirmResultData, ConfirmResultDialog } from './confirm-result.dialog';

/** Panel del admin (specs/03-admin-results). */
@Component({
  selector: 'app-admin-page',
  imports: [DatePipe, MatButtonModule, MatProgressBarModule, Icon, TeamLabel, ScoreStepper],
  templateUrl: './admin.page.html',
  styleUrl: './admin.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPage {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly clock = inject(Clock);

  protected readonly matches = rxResource({ stream: () => this.api.invoke(listMatchesForAdmin) });
  /** Marcadores en edición por id de partido. */
  protected readonly drafts = signal<Record<number, Score>>({});
  protected readonly savingId = signal<number | null>(null);

  protected readonly finishedCount = computed(
    () => (this.matches.value() ?? []).filter((match) => match.status === 'FINISHED').length,
  );
  protected readonly errorMessage = computed(() => {
    const error = this.matches.error();
    return error ? problemMessage(error) : null;
  });

  protected draft(match: AdminMatch): Score {
    return this.drafts()[match.id] ?? match.result ?? { homeGoals: 0, awayGoals: 0 };
  }

  protected setGoals(match: AdminMatch, side: keyof Score, goals: number): void {
    this.drafts.update((drafts) => ({ ...drafts, [match.id]: { ...this.draft(match), [side]: goals } }));
  }

  protected isUnchanged(match: AdminMatch): boolean {
    const draft = this.draft(match);
    return !!match.result && match.result.homeGoals === draft.homeGoals && match.result.awayGoals === draft.awayGoals;
  }

  protected async save(match: AdminMatch): Promise<void> {
    const score = this.draft(match);
    const data: ConfirmResultData = {
      match,
      score,
      startsInFuture: new Date(match.kickoffAt).getTime() > this.clock.now(),
    };
    const confirmed = await firstValueFrom(this.dialog.open(ConfirmResultDialog, { data }).afterClosed());
    if (!confirmed) {
      return;
    }

    this.savingId.set(match.id);
    try {
      const updated = await firstValueFrom(
        this.api.invoke(registerResult, { matchId: match.id, body: { ...score, version: match.version } }),
      );
      this.matches.value.update((matches) => matches?.map((m) => (m.id === updated.id ? updated : m)));
      this.drafts.update(({ [match.id]: _, ...rest }) => rest);
      this.snackBar.open('Resultado guardado. Los puntos se están recalculando.', undefined, { duration: 3500 });
    } catch (error) {
      this.snackBar.open(problemMessage(error), 'Cerrar', { duration: 6000 });
      if (problemCode(error) === 'stale-version') {
        this.matches.reload();
      }
    } finally {
      this.savingId.set(null);
    }
  }
}
