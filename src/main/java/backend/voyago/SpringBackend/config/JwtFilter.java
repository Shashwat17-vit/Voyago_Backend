package backend.voyago.SpringBackend.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil)
    {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException
    {
        String token = extractTokenFromCookies(request);

        // If a valid JWT cookie exists, authenticate the user in Spring's security context
        if (token != null && jwtUtil.isTokenValid(token))
        {
            String email = jwtUtil.extractEmail(token);

            // Tell Spring Security this request belongs to this email
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(email, null, List.of());

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // Continue processing the request regardless — Spring's rules decide what's allowed
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookies(HttpServletRequest request)
    {
        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies())
        {
            if ("jwt".equals(cookie.getName()))
            {
                return cookie.getValue();
            }
        }
        return null;
    }
}
