package dev.leiber.polla.auth.web;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leiber.polla.auth.internal.AuthService;
import dev.leiber.polla.auth.internal.AuthService.AuthResult;
import dev.leiber.polla.auth.internal.LoginRateLimiter;
import dev.leiber.polla.auth.web.AuthDtos.AuthResponse;
import dev.leiber.polla.auth.web.AuthDtos.LoginRequest;
import dev.leiber.polla.auth.web.AuthDtos.RegisterRequest;
import dev.leiber.polla.auth.web.AuthDtos.UserResponse;
import dev.leiber.polla.shared.security.CurrentUser;
import dev.leiber.polla.shared.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter rateLimiter;
    private final SecurityProperties.RefreshToken cookieSettings;

    AuthController(AuthService authService, LoginRateLimiter rateLimiter, SecurityProperties properties) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.cookieSettings = properties.refreshToken();
    }

    @PostMapping("/auth/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        var result = authService.register(request.email(), request.displayName(), request.password());
        return withRefreshCookie(ResponseEntity.status(HttpStatus.CREATED), result);
    }

    @PostMapping("/auth/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.acquire(http.getRemoteAddr());
        var result = authService.login(request.email(), request.password());
        return withRefreshCookie(ResponseEntity.ok(), result);
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = "${app.security.refresh-token.cookie-name}",
            required = false) String refreshToken) {
        var result = authService.refresh(refreshToken);
        return withRefreshCookie(ResponseEntity.ok(), result);
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@CookieValue(name = "${app.security.refresh-token.cookie-name}",
            required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.getUser(CurrentUser.id(jwt)));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(ResponseEntity.BodyBuilder builder, AuthResult result) {
        var body = new AuthResponse(result.accessToken().value(), result.accessToken().expiresInSeconds(),
                UserResponse.from(result.user()));
        return builder
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), cookieSettings.ttl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieSettings.cookieName(), value)
                .httpOnly(true)
                .secure(cookieSettings.cookieSecure())
                .sameSite("Strict")
                .path(cookieSettings.cookiePath())
                .maxAge(maxAge)
                .build();
    }
}
