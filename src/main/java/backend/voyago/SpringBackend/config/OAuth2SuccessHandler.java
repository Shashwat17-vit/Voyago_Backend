package backend.voyago.SpringBackend.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String name  = oAuth2User.getAttribute("name") != null
                       ? oAuth2User.getAttribute("name")
                       : oAuth2User.getAttribute("login"); // GitHub fallback
        String email = oAuth2User.getAttribute("email");

        // TODO Step 6: save user to DB here

        
        // TODO Step 7: generate JWT and append as ?token=jwt here

        response.sendRedirect("http://localhost:5173/#/home");
    }
}
