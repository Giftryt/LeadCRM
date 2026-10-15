package com.leadtracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadtracker.auth.JwtService;
import com.leadtracker.auth.SessionAuthFilter;
import com.leadtracker.common.ErrorBody;
import com.leadtracker.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtService jwt, UserRepository users, AppProperties props, ObjectMapper mapper)
      throws Exception {
    List<String> origins = props.corsOriginList();
    if (origins.isEmpty()) {
      // Same-origin deployment (nginx / Vite proxy in front): no CORS handling needed.
      http.cors(c -> c.disable());
    } else {
      CorsConfiguration cors = new CorsConfiguration();
      cors.setAllowedOrigins(origins);
      cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
      cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
      cors.setAllowCredentials(true);
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/api/**", cors);
      http.cors(c -> c.configurationSource(source));
    }

    http
        // The session cookie is SameSite=Lax and every write takes a JSON or multipart body, which blocks
        // cross-site form posts; a CSRF token is not needed for this API.
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable())
        .requestCache(r -> r.disable())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          Object reason = req.getAttribute(SessionAuthFilter.FAILURE_ATTR);
                          write(res, mapper, 401, "unauthorized", reason != null ? reason.toString() : "Sign in to continue.");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> write(res, mapper, 403, "forbidden", "You don't have permission to do that.")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/health", "/api/auth/login", "/api/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(new SessionAuthFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private static void write(HttpServletResponse res, ObjectMapper mapper, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    res.setCharacterEncoding("UTF-8");
    mapper.writeValue(res.getOutputStream(), ErrorBody.of(code, message));
  }
}
