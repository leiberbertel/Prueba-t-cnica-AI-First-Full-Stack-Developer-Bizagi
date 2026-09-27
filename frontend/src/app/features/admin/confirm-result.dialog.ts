import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

import { AdminMatch, Score } from '../../api/models';

export interface ConfirmResultData {
  match: AdminMatch;
  score: Score;
  startsInFuture: boolean;
}

/** Confirmación antes de registrar un resultado (CA-03.6). */
@Component({
  selector: 'app-confirm-result-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.match.status === 'FINISHED' ? 'Corregir resultado' : 'Registrar resultado' }}</h2>
    <mat-dialog-content>
      <p class="score">
        {{ data.match.homeTeam.name }} <strong>{{ data.score.homeGoals }} - {{ data.score.awayGoals }}</strong>
        {{ data.match.awayTeam.name }}
      </p>
      <p>
        Se recalcularán los puntos de <strong>{{ data.match.predictionsCount }}</strong>
        {{ data.match.predictionsCount === 1 ? 'predicción' : 'predicciones' }} y el partido quedará cerrado.
      </p>
      @if (data.startsInFuture) {
        <p class="warning">Atención: este partido aún no ha comenzado según el calendario.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button [mat-dialog-close]="true" cdkFocusInitial>Confirmar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .score { font: 600 1.15rem Outfit, sans-serif; }
    .score strong { padding: 2px 10px; margin: 0 6px; border-radius: 8px; background: var(--app-pitch); color: #fff; }
    .warning { color: var(--app-warning); font-weight: 600; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmResultDialog {
  protected readonly data = inject<ConfirmResultData>(MAT_DIALOG_DATA);
}
