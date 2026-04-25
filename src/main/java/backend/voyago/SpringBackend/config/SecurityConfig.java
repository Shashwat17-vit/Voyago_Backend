package backend.voyago.SpringBackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final OAuth2SuccessHandler successHandler;

    public SecurityConfig(OAuth2SuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth->auth.requestMatchers("/", "/login", "/error").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth->oauth.successHandler(successHandler));

        return http.build();
    }

}
