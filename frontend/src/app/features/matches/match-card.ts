import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

import { Api } from '../../api/api';
import { upsertPrediction } from '../../api/functions';
import { Match, Prediction } from '../../api/models';
import { problemCode, problemMessage } from '../../core/http/problem';
import { Clock, timeUntil } from '../../core/time/clock';
import { Icon } from '../../core/ui/icon';
import { TeamLabel } from '../../core/ui/team-label';
import { pointsBadge } from './scoring';
import { ScoreStepper } from './score-stepper';

@Component({
  selector: 'app-match-card',
  imports: [DatePipe, MatButtonModule, Icon, TeamLabel, ScoreStepper],
  templateUrl: './match-card.html',
  styleUrl: './match-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.finished]': 'match().status === "FINISHED"',
    '[class.closed]': '!open()',
  },
})
export class MatchCard {
  private readonly api = inject(Api);
  private readonly snackBar = inject(MatSnackBar);
  private readonly clock = inject(Clock);

  readonly match = input.required<Match>();
  /** El admin no participa (RN-09): ve los partidos pero no predice. */
  readonly canPredict = input(true);
  readonly saved = output<Prediction>();
  /** El servidor cerró el partido mientras el usuario lo tenía abierto. */
  readonly closedByServer = output<void>();

  protected readonly home = linkedSignal(() => this.match().myPrediction?.homeGoals ?? 0);
  protected readonly away = linkedSignal(() => this.match().myPrediction?.awayGoals ?? 0);
  protected readonly saving = signal(false);

  private readonly kickoff = computed(() => new Date(this.match().kickoffAt).getTime());
  protected readonly open = computed(() => this.match().predictionOpen && this.clock.now() < this.kickoff());
  protected readonly closesIn = computed(() => timeUntil(this.kickoff(), this.clock.now()));
  protected readonly badge = computed(() => pointsBadge(this.match().myPrediction?.points));
  protected readonly dirty = computed(() => {
    const prediction = this.match().myPrediction;
    return !prediction || prediction.homeGoals !== this.home() || prediction.awayGoals !== this.away();
  });

  protected async save(): Promise<void> {
    this.saving.set(true);
    try {
      const prediction = await firstValueFrom(
        this.api.invoke(upsertPrediction, {
          matchId: this.match().id,
          body: { homeGoals: this.home(), awayGoals: this.away() },
        }),
      );
      this.saved.emit(prediction);
      this.snackBar.open(
        `Predicción guardada: ${this.match().homeTeam.name} ${prediction.homeGoals} - ${prediction.awayGoals} ${this.match().awayTeam.name}`,
        undefined,
        { duration: 3000 },
      );
    } catch (error) {
      this.snackBar.open(problemMessage(error), 'Cerrar', { duration: 5000 });
      if (problemCode(error) === 'prediction-closed') {
        this.closedByServer.emit();
      }
    } finally {
      this.saving.set(false);
    }
  }
}
