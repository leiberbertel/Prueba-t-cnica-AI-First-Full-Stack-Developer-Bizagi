package dev.leiber.polla.shared.security;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Codificador y decodificador JWT (HS256) con las utilidades nativas de Spring Security (ADR-0004).
 */
@Configuration(proxyBeanMethods = false)
class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        var secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            if (properties.jwt().ephemeralSecretAllowed()) {
                log.warn("JWT_SECRET no configurado: usando una clave efímera (solo desarrollo local). "
                        + "Las sesiones se invalidan al reiniciar.");
                byte[] random = new byte[48];
                new SecureRandom().nextBytes(random);
                return new SecretKeySpec(random, "HmacSHA256");
            }
            throw new IllegalStateException(
                    "Falta JWT_SECRET (Base64, >= 32 bytes). Genera uno con: openssl rand -base64 48");
        }
        byte[] bytes = Base64.getDecoder().decode(secret.trim());
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos %d bytes".formatted(MIN_SECRET_BYTES));
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, SecurityProperties properties) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtIssuerValidator(properties.jwt().issuer())));
        return decoder;
    }
}
