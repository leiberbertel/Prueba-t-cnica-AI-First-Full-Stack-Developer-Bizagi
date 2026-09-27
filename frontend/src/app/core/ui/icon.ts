import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Ícono de Material Symbols decorativo (oculto a lectores de pantalla). */
@Component({
  selector: 'app-icon',
  template: `<span class="material-symbols-rounded" [style.font-size.px]="size()" aria-hidden="true">{{ name() }}</span>`,
  host: { style: 'display: inline-flex' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  readonly name = input.required<string>();
  readonly size = input(20);
}
