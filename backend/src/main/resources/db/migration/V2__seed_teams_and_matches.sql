-- Seed: 2 grupos x 4 selecciones, 12 partidos (todos contra todos, 3 fechas).
-- Edición demo: equipos reales, calendario ficticio (horas en UTC).

insert into teams (code, name, flag_code, group_code) values
    ('COL', 'Colombia',  'co', 'A'),
    ('ARG', 'Argentina', 'ar', 'A'),
    ('ESP', 'España',    'es', 'A'),
    ('JPN', 'Japón',     'jp', 'A'),
    ('BRA', 'Brasil',    'br', 'B'),
    ('FRA', 'Francia',   'fr', 'B'),
    ('MEX', 'México',    'mx', 'B'),
    ('MAR', 'Marruecos', 'ma', 'B');

insert into matches (group_code, matchday, home_team_id, away_team_id, kickoff_at, venue)
select s.group_code, s.matchday, h.id, a.id, s.kickoff_at, s.venue
from (values
    ('A', 1, 'COL', 'JPN', timestamptz '2026-11-20 17:00:00+00', 'Ciudad de México'),
    ('A', 1, 'ARG', 'ESP', timestamptz '2026-11-20 20:00:00+00', 'Nueva York'),
    ('B', 1, 'BRA', 'MAR', timestamptz '2026-11-21 17:00:00+00', 'Los Ángeles'),
    ('B', 1, 'FRA', 'MEX', timestamptz '2026-11-21 20:00:00+00', 'Monterrey'),
    ('A', 2, 'COL', 'ARG', timestamptz '2026-11-24 17:00:00+00', 'Miami'),
    ('A', 2, 'ESP', 'JPN', timestamptz '2026-11-24 20:00:00+00', 'Toronto'),
    ('B', 2, 'BRA', 'FRA', timestamptz '2026-11-25 17:00:00+00', 'Dallas'),
    ('B', 2, 'MEX', 'MAR', timestamptz '2026-11-25 20:00:00+00', 'Guadalajara'),
    ('A', 3, 'JPN', 'ARG', timestamptz '2026-11-28 19:00:00+00', 'Seattle'),
    ('A', 3, 'ESP', 'COL', timestamptz '2026-11-28 19:00:00+00', 'Atlanta'),
    ('B', 3, 'MAR', 'FRA', timestamptz '2026-11-29 19:00:00+00', 'Vancouver'),
    ('B', 3, 'MEX', 'BRA', timestamptz '2026-11-29 19:00:00+00', 'Houston')
) as s (group_code, matchday, home, away, kickoff_at, venue)
join teams h on h.code = s.home
join teams a on a.code = s.away;
