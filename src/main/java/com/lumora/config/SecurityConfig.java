package com.lumora.config;

import com.lumora.common.web.RateLimitProperties;
import com.lumora.identity.security.LocalUserAuthenticationConverter;
import com.lumora.identity.security.SecurityErrorHandler;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({LumoraCorsProperties.class, RateLimitProperties.class})
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, LocalUserAuthenticationConverter converter, SecurityErrorHandler errors) {
    return http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .headers(
            headers ->
                headers
                    .contentTypeOptions(Customizer.withDefaults())
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(
                        permissions ->
                            permissions.policy("camera=(), microphone=(), geolocation=()")))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/health",
                        "/api/health/liveness",
                        "/api/health/readiness",
                        "/actuator/health",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/properties",
                        "/api/properties/*",
                        "/api/properties/*/room-types",
                        "/api/properties/*/room-types/*",
                        "/api/properties/*/rooms",
                        "/api/properties/*/rooms/*",
                        "/api/public/properties",
                        "/api/public/properties/*",
                        "/api/public/properties/*/rooms",
                        "/api/public/rooms/*",
                        "/api/public/availability",
                        "/api/amenities")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/webhooks/payments/*")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/manager/**")
                    .hasAnyRole("MANAGER", "ADMIN")
                    .requestMatchers("/api/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/bookings")
                    .hasRole("USER")
                    .requestMatchers(HttpMethod.POST, "/api/bookings/*/payments")
                    .hasRole("USER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/bookings/*/payments",
                        "/api/payments/*/qr",
                        "/api/bookings/*/payments/*/qr",
                        "/api/bookings/*/cancellation-policy",
                        "/api/me/bookings/*/cancellation-policy",
                        "/api/bookings/*/refund",
                        "/api/me/bookings/*/refund",
                        "/api/bookings/*/check-in-qr",
                        "/api/me/bookings/*/check-in-qr",
                        "/api/bookings/*/access-code",
                        "/api/me/bookings/*/access-code")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/bookings/*/cancel",
                        "/api/me/bookings/*/cancel",
                        "/api/mock-payments/*/success",
                        "/api/mock-payments/*/fail",
                        "/api/mock-payments/*/cancel")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/me/bookings", "/api/me/bookings/*")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            resource ->
                resource
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint(errors)
                    .accessDeniedHandler(errors))
        .exceptionHandling(
            handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(LumoraCorsProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        properties.allowedOrigins().stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Request-Id",
            "X-Webhook-Signature",
            "X-Rate-Limit-Client"));
    config.setExposedHeaders(
        List.of("X-Request-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
