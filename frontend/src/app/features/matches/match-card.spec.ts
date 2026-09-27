import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Match } from '../../api/models';
import { MatchCard } from './match-card';

const FUTURE = new Date(Date.now() + 3 * 86_400_000).toISOString();

function match(overrides: Partial<Match> = {}): Match {
  return {
    id: 7,
    group: 'A',
    matchday: 1,
    homeTeam: { code: 'COL', name: 'Colombia', flagCode: 'co' },
    awayTeam: { code: 'JPN', name: 'Japón', flagCode: 'jp' },
    kickoffAt: FUTURE,
    venue: 'Ciudad de México',
    status: 'SCHEDULED',
    result: null,
    predictionOpen: true,
    myPrediction: null,
    ...overrides,
  };
}

describe('MatchCard', () => {
  let fixture: ComponentFixture<MatchCard>;
  let backend: HttpTestingController;

  function render(value: Match, canPredict = true): HTMLElement {
    fixture = TestBed.createComponent(MatchCard);
    fixture.componentRef.setInput('match', value);
    fixture.componentRef.setInput('canPredict', canPredict);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function button(element: HTMLElement, label: string): HTMLButtonElement {
    const found = [...element.querySelectorAll('button')].find((b) => b.textContent?.includes(label));
    if (!found) throw new Error(`Button "${label}" not found`);
    return found;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MatchCard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  it('sends the prediction chosen with the steppers', async () => {
    const element = render(match());
    element.querySelector<HTMLButtonElement>('[aria-label="Más goles para Colombia"]')!.click();
    element.querySelector<HTMLButtonElement>('[aria-label="Más goles para Colombia"]')!.click();
    fixture.detectChanges();

    button(element, 'Guardar').click();

    const request = backend.expectOne('/api/v1/matches/7/prediction');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ homeGoals: 2, awayGoals: 0 });
    request.flush({ matchId: 7, homeGoals: 2, awayGoals: 0, points: null });
  });

  it('disables saving when the prediction is unchanged', () => {
    const element = render(match({ myPrediction: { matchId: 7, homeGoals: 1, awayGoals: 1 } }));

    expect(button(element, 'Guardada').disabled).toBe(true);
  });

  it('shows result and points once the match is finished', () => {
    const element = render(
      match({
        status: 'FINISHED',
        predictionOpen: false,
        result: { homeGoals: 2, awayGoals: 1 },
        myPrediction: { matchId: 7, homeGoals: 2, awayGoals: 1, points: 3 },
      }),
    );

    expect(element.textContent).toContain('Tu predicción: 2 - 1');
    expect(element.querySelector('.badge--exact')?.textContent).toContain('+3 Exacto');
    expect(element.querySelector('app-score-stepper')).toBeNull();
  });

  it('never shows the prediction form to the admin (RN-09)', () => {
    const element = render(match(), false);

    expect(element.querySelector('app-score-stepper')).toBeNull();
    expect(element.textContent).toContain('Abierto');
  });
});
