package dev.leiber.polla.auth;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * Evento de dominio: un participante eliminó su cuenta (HU-01.6). Sus predicciones y sesiones ya fueron borradas por
 * la base de datos ({@code ON DELETE CASCADE}); el evento permite que otros módulos reaccionen (auditoría,
 * notificaciones) sin acoplarse a {@code auth}.
 */
@DomainEvent
public record UserAccountDeleted(long userId) {
}
