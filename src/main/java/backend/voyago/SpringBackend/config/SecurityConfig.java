package backend.voyago.SpringBackend.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final OAuth2SuccessHandler successHandler;
    private final JwtFilter jwtFilter;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOriginsCsv;

    public SecurityConfig(OAuth2SuccessHandler successHandler, JwtFilter jwtFilter) {
        this.successHandler = successHandler;
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/login", "/error",
                                     "/api/auth/signup", "/api/auth/login", "/api/auth/logout").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                    .successHandler(successHandler)
                    .failureUrl(frontendUrl + "/login"))
                .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                    )
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(resolvedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Exact-origin CORS. Also allows the www/apex twin so https://voyago.dev
     * and https://www.voyago.dev both work when only one is configured.
     */
    private List<String> resolvedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        addOriginAndVariants(origins, allowedOriginsCsv);
        addOriginAndVariants(origins, frontendUrl);
        if (origins.isEmpty()) {
            origins.add("http://localhost:5173");
        }
        return new ArrayList<>(origins);
    }

    private void addOriginAndVariants(Set<String> out, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String raw : csv.split(",")) {
            String origin = stripTrailingSlash(raw.trim());
            if (origin.isEmpty()) {
                continue;
            }
            out.add(origin);
            URI uri = URI.create(origin);
            String host = uri.getHost();
            String scheme = uri.getScheme();
            if (host == null || scheme == null) {
                continue;
            }
            String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            if (host.startsWith("www.")) {
                out.add(scheme + "://" + host.substring(4) + port);
            } else {
                out.add(scheme + "://www." + host + port);
            }
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
