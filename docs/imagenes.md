# Imágenes generadas con IA (Nano Banana / Gemini)

La UI funciona sin imágenes (la cancha del login está dibujada con CSS). Estas imágenes son el toque visual final.

**Reglas:** nada de logos oficiales (FIFA, Mundial, federaciones), camisetas reales ni jugadores reconocibles.
Las imágenes deben ser genéricas y propias.

| Archivo | Dónde se usa | Tamaño sugerido |
|---|---|---|
| `frontend/public/images/hero.webp` | Panel izquierdo del login y del registro | 1600×1800, vertical |

## Prompts

### hero.webp

```
Editorial illustration for a football prediction app login screen. Night stadium seen from pitch level,
dramatic floodlights, deep emerald green grass with mowing stripes, a single white football in the foreground
catching golden rim light, subtle confetti in gold, cinematic depth of field. Color palette: deep green #0b3d2e,
emerald, warm gold #f2c94c, touches of white. No text, no logos, no brand marks, no recognizable people,
no real team jerseys. Vertical composition, empty dark area in the lower third for overlaid text. High detail, 4k.
```


## Cómo activarlas

1. Guarda la imagen en `frontend/public/images/` (convertir a WebP: `npx sharp-cli -i hero.png -o hero.webp`).
2. Login/registro: en `frontend/src/app/features/auth/auth-layout.scss` define
   `:host { --auth-hero-image: url('/images/hero.webp'); }`.
3. Registra el prompt y la iteración en `docs/AI_LOG.md`.
