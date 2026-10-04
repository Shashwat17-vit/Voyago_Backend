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
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.UserRepository;
import backend.voyago.SpringBackend.service.AuthService;
import backend.voyago.SpringBackend.service.TripLimitService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final TripLimitService tripLimitService;

    public LoginController(AuthService authService, UserRepository userRepository,
                           TripLimitService tripLimitService)
    {
        this.authService = authService;
        this.userRepository = userRepository;
        this.tripLimitService = tripLimitService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication)
    {
        if (authentication == null || !authentication.isAuthenticated())
        {
            return ResponseEntity.status(401).build();
        }

        String email;
        String fallbackName = "";
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            email = oAuth2User.getAttribute("email");
            fallbackName = oAuth2User.getAttribute("name") != null
                    ? oAuth2User.getAttribute("name") : "";
        } else {
            email = (String) authentication.getPrincipal();
        }

        User user = email != null ? userRepository.findOneByEmail(email).orElse(null) : null;
        String name = user != null && user.getFull_name() != null && !user.getFull_name().isBlank()
                ? user.getFull_name()
                : (fallbackName != null && !fallbackName.isBlank() ? fallbackName : email);
        String tag = user != null ? user.getTag() : null;
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("uid", user != null ? user.getUid() : null);
        body.put("email", email != null ? email : "");
        body.put("name", name != null ? name : "");
        body.put("tag", tag);
        body.put("handle", tag == null || tag.isBlank() ? "" : "#" + tag);
        if (user != null) {
            int tripCount = tripLimitService.countFor(user);
            body.put("tripCount", tripCount);
            body.put("tripLimit", TripLimitService.MAX_TRIPS);
            body.put("canAddTrip", tripCount < TripLimitService.MAX_TRIPS);
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // immediately expire
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> loginUser(
            @RequestBody LoginRequest request,
            HttpServletResponse response)
    {
        try {
            String token = authService.login(request);

            Cookie cookie = new Cookie("jwt", token);
            cookie.setHttpOnly(true);   // JS cannot read this — protected from XSS
            cookie.setSecure(true);    // set to true in production (requires HTTPS)
            cookie.setPath("/");        // send cookie on every request to this server
            cookie.setMaxAge(60 * 60 * 24); // 24 hours

            response.addCookie(cookie);

            return ResponseEntity.ok(Map.of("message", "Login successful"));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
