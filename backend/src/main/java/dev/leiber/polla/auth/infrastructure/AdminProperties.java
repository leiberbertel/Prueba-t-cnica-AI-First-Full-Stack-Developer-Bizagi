package dev.leiber.polla.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.admin")
record AdminProperties(String email, String password, String displayName) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
