import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Team } from '../../api/models';

/** Bandera + nombre de una selección. Banderas SVG locales (public/flags), sin peticiones externas. */
@Component({
  selector: 'app-team',
  imports: [NgOptimizedImage],
  template: `
    <img class="flag" [ngSrc]="'flags/' + team().flagCode + '.svg'" width="32" height="24" alt="" />
    <span class="name">{{ team().name }}</span>
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 10px; min-width: 0; }
    :host(.reverse) { flex-direction: row-reverse; text-align: right; }
    .flag { flex: none; }
    .name { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamLabel {
  readonly team = input.required<Team>();
}
