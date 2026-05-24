package com.example.photomanagement.config;

import com.example.photomanagement.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Stateless Bearer-JWT security split across two filter chains.
 *
 * <p>The first chain ({@link #logoutFilterChain}) matches only {@code POST /api/auth/logout}. It
 * carries no oauth2 resource server, so a stale or invalid {@code Authorization: Bearer ...} header
 * on a logout request does not trigger token validation (which would otherwise return 401 and
 * prevent the client from clearing its rotation cookie after the access token expired).
 *
 * <p>The second chain ({@link #mainFilterChain}) covers everything else with the standard JWT
 * resource server. CSRF protection on cookie-bearing endpoints relies on {@code SameSite=Strict +
 * Path=/api/auth/refresh}; other state-changing endpoints are Bearer-authenticated and therefore
 * intrinsically CSRF-safe.
 */
@Configuration
public class SecurityConfig {

  @Bean
  @Order(1)
  public SecurityFilterChain logoutFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(new AntPathRequestMatcher("/api/auth/logout", HttpMethod.POST.name()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public SecurityFilterChain mainFilterChain(HttpSecurity http, JwtService jwtService)
      throws Exception {
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/refresh")
                    .permitAll()
                    .requestMatchers(
                        "/api/health",
                        "/actuator/health",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            rs ->
                rs.jwt(
                    jwt ->
                        jwt.decoder(jwtService.jwtDecoder())
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(JwtService.CLAIM_ROLES);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }
}
