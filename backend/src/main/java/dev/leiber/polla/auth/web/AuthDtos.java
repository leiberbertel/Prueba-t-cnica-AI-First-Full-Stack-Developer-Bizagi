package dev.leiber.polla.auth.web;

import dev.leiber.polla.auth.domain.UserAccount;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs del contrato (tag Auth en specs/api/openapi.yaml). */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @NotBlank(message = "El email es obligatorio") @Email(message = "Email inválido")
            @Size(max = 254, message = "Máximo 254 caracteres") String email,
            @NotBlank(message = "El nombre es obligatorio")
            @Size(min = 2, max = 40, message = "Entre 2 y 40 caracteres") String displayName,
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 72, message = "Entre 8 y 72 caracteres")
            @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "Debe tener al menos una letra y un número")
            String password) {

        /** Se recortan espacios antes de validar (caso borde de specs/01-auth). */
        RegisterRequest {
            email = email == null ? null : email.trim();
            displayName = displayName == null ? null : displayName.trim();
        }
    }

    record LoginRequest(
            @NotBlank(message = "El email es obligatorio") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {

        LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    record DeleteAccountRequest(
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    record UserResponse(Long id, String email, String displayName, String role) {

        static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
        }
    }

    record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
    }
}
