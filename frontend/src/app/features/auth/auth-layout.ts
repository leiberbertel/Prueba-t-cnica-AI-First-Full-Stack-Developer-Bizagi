import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Layout de login/registro: panel ilustrado + formulario. */
@Component({
  selector: 'app-auth-layout',
  template: `
    <div class="hero" aria-hidden="true">
      <div class="hero__content">
        <img src="favicon.svg" alt="" width="56" height="56" />
        <p class="hero__kicker">Fase de grupos · Edición demo</p>
        <p class="hero__title">Predice. Suma. <span>Gana la polla.</span></p>
        <ul class="hero__rules">
          <li><strong>3 pts</strong> marcador exacto</li>
          <li><strong>1 pt</strong> ganador o empate</li>
        </ul>
      </div>
    </div>
    <section class="panel">
      <div class="panel__inner">
        <h1>{{ heading() }}</h1>
        <p class="panel__subtitle">{{ subtitle() }}</p>
        <ng-content />
      </div>
    </section>
  `,
  styleUrl: './auth-layout.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthLayout {
  readonly heading = input.required<string>();
  readonly subtitle = input('');
}
