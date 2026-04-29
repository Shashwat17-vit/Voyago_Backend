package backend.voyago.SpringBackend.config;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    // Secret key used to sign the token — must be at least 256 bits for HMAC-SHA256
    // In production this should come from application.properties, not hardcoded
    private static final String SECRET = "voyago-super-secret-key-must-be-32-chars!!";
    private static final long EXPIRY_MS = 1000L * 60 * 60 * 24; // 24 hours

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // Called after successful login — builds a signed JWT with the user's email inside
    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)                              // who this token belongs to
                .issuedAt(new Date())                        // when it was created
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS)) // when it expires
                .signWith(getSigningKey())                   // sign it so it can't be faked
                .compact();                                  // serialize to a string
    }

    // Called on every protected request — cracks open the token and returns the email
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // Quick check — has the token expired?
    public boolean isTokenValid(String token) {
        try {
            extractEmail(token); // throws if expired or tampered
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
