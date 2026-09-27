import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

import { Icon } from '../../core/ui/icon';

const MAX_GOALS = 20;

/** Selector de goles accesible: botones −/+ y campo numérico (RN-03: 0 a 20). */
@Component({
  selector: 'app-score-stepper',
  imports: [Icon],
  template: `
    <button type="button" class="step" (click)="change(-1)" [disabled]="disabled() || value() <= 0"
            [attr.aria-label]="'Menos goles para ' + label()">
      <app-icon name="remove" [size]="18" />
    </button>
    <input type="number" inputmode="numeric" min="0" [attr.max]="max" [value]="value()"
           [disabled]="disabled()" [attr.aria-label]="'Goles de ' + label()"
           (change)="set($any($event.target).value)" />
    <button type="button" class="step" (click)="change(1)" [disabled]="disabled() || value() >= max"
            [attr.aria-label]="'Más goles para ' + label()">
      <app-icon name="add" [size]="18" />
    </button>
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 4px; }
    input {
      width: 48px; height: 48px; text-align: center;
      font: 700 1.5rem/1 Outfit, sans-serif; color: inherit;
      background: var(--mat-sys-surface); border: 1px solid var(--mat-sys-outline-variant); border-radius: 12px;
      -moz-appearance: textfield;
    }
    input::-webkit-inner-spin-button, input::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }
    input:disabled { opacity: 0.7; }
    .step {
      display: grid; place-items: center; width: 32px; height: 32px; padding: 0;
      border: 0; border-radius: 50%; cursor: pointer; color: var(--mat-sys-on-secondary-container);
      background: var(--mat-sys-secondary-container);
    }
    .step:disabled { opacity: 0.35; cursor: default; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScoreStepper {
  readonly value = model(0);
  readonly label = input.required<string>();
  readonly disabled = input(false);
  protected readonly max = MAX_GOALS;

  protected change(delta: number): void {
    this.value.update((current) => clamp(current + delta));
  }

  protected set(raw: string): void {
    const parsed = Number.parseInt(raw, 10);
    this.value.set(Number.isNaN(parsed) ? 0 : clamp(parsed));
  }
}

function clamp(goals: number): number {
  return Math.min(MAX_GOALS, Math.max(0, goals));
}
