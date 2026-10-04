package backend.voyago.SpringBackend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * JWT login sets the principal to the email string; OAuth2 login sets an OAuth2User.
 */
final class CurrentUser {

    private CurrentUser() {
    }

    static String email(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Not signed in");
        }
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        return (String) authentication.getPrincipal();
    }
}
