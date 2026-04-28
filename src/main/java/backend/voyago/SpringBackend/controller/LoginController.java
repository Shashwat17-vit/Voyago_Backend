package backend.voyago.SpringBackend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.dto.LoginRequest;
import backend.voyago.SpringBackend.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final AuthService authService;

    public LoginController(AuthService authService)
    {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication)
    {
        if (authentication == null || !authentication.isAuthenticated())
        {
            return ResponseEntity.status(401).build();
        }

        // OAuth2 login — principal is an OAuth2User with name/email attributes
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User)
        {
            return ResponseEntity.ok(Map.of(
                "name",  oAuth2User.getAttribute("name")  != null ? oAuth2User.getAttribute("name")  : "",
                "email", oAuth2User.getAttribute("email") != null ? oAuth2User.getAttribute("email") : ""
            ));
        }

        // JWT login — principal is the email string set by JwtFilter
        String email = (String) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("email", email, "name", ""));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> loginUser(
            @RequestBody LoginRequest request,
            HttpServletResponse response)   // Spring injects this so we can set a cookie
    {
        try {
            String token = authService.login(request);

            // Build the HttpOnly cookie
            Cookie cookie = new Cookie("jwt", token);
            cookie.setHttpOnly(true);   // JS cannot read this — protected from XSS
            cookie.setSecure(false);    // set to true in production (requires HTTPS)
            cookie.setPath("/");        // send cookie on every request to this server
            cookie.setMaxAge(60 * 10); // 10 minutes in seconds

            response.addCookie(cookie);

            return ResponseEntity.ok(Map.of("message", "Login successful"));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
