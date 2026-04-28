package backend.voyago.SpringBackend.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public OAuth2SuccessHandler(UserRepository userRepository) 
    {
        this.userRepository = userRepository ;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken token= (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = token.getPrincipal() ;

        String provider = token.getAuthorizedClientRegistrationId();

        String name  = oAuth2User.getAttribute("name") != null
                       ? oAuth2User.getAttribute("name")
                       : oAuth2User.getAttribute("login"); // GitHub fallback
        String email = oAuth2User.getAttribute("email");

        if (email !=null && !userRepository.existsByEmail(email))
        {
            User user = new User();
            user.setFull_name(name);
            user.setEmail(email);
            user.setPassword(null);
            user.setProvider(provider);
            userRepository.save(user);
        }

        // TODO Step 7: generate JWT and append as ?token=jwt here

        response.sendRedirect("http://localhost:5173/#/home");
    }
}
