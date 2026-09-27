/**
 * Lado de lectura (read model): ranking, resumen personal e historiales. Consultas SQL agregadas sobre las tablas
 * de los demás módulos, sin modificar datos. Spec: specs/04-leaderboard/spec.md
 */
@ApplicationModule(displayName = "Ranking")
package dev.leiber.polla.leaderboard;

import org.springframework.modulith.ApplicationModule;
