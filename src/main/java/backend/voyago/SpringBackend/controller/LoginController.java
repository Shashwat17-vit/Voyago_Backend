package backend.voyago.SpringBackend.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class LoginController {

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(Authentication authentication)
    {   
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        return Map.of(
            "name", oAuth2User.getAttribute("name"),
            "email", oAuth2User.getAttribute("email")
        );

    }

}
