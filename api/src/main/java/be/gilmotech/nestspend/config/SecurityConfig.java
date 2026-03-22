package be.gilmotech.nestspend.config;

import be.gilmotech.nestspend.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;

/**
 * Configuration de la sécurité Spring Security.
 * L'API est stateless (JWT), la protection CSRF est désactivée intentionnellement.
 * La console H2 n'est exposée qu'en profil dev.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthFilter jwtAuthFilter;
    private final Environment environment;

    public SecurityConfig(CorsConfigurationSource corsConfigurationSource,
                          JwtAuthFilter jwtAuthFilter,
                          Environment environment) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthFilter = jwtAuthFilter;
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Détermine si le profil actif est "dev" pour exposer la console H2
        boolean isDevProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // La protection CSRF est désactivée car cette API REST utilise des tokens JWT stateless.
            // Les attaques CSRF exploitent les cookies de session, absents dans une API stateless.
            .csrf(csrf -> csrf.disable())  // lgtm[java/spring-disabled-csrf-protection]
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                })
            )
            .authorizeHttpRequests(auth -> {
                // Endpoints publics communs
                auth.requestMatchers(
                    "/api/auth/**",
                    "/api/health",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health"
                ).permitAll();

                // Console H2 exposée uniquement en environnement de développement
                if (isDevProfile) {
                    auth.requestMatchers("/h2/**", "/actuator/**").permitAll();
                }

                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Autorisation des iframes pour la console H2 (sameOrigin uniquement)
        if (isDevProfile) {
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        return http.build();
    }
}
