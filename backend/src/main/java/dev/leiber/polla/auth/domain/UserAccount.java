package dev.leiber.polla.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Distinto de null: la cuenta fue eliminada y sus datos se están purgando (ADR-0007). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String displayName, String passwordHash, Role role, Instant createdAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    /**
     * Fase síncrona de la eliminación (HU-01.6): se anonimizan los datos personales de inmediato. La fila solo se
     * conserva hasta que los demás módulos purguen sus datos. El email queda libre para registrarse de nuevo.
     */
    public void markDeleted(Instant now) {
        this.email = "deleted+" + id + "@polla.invalid";
        this.displayName = "Cuenta eliminada";
        this.passwordHash = "!"; // no es un hash BCrypt válido: ninguna contraseña coincide
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }
}
