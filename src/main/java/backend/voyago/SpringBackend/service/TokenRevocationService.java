package backend.voyago.SpringBackend.service;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.voyago.SpringBackend.config.JwtUtil;
import backend.voyago.SpringBackend.model.RevokedToken;
import backend.voyago.SpringBackend.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;

@Service
public class TokenRevocationService {

    private final RevokedTokenRepository repository;
    private final JwtUtil jwtUtil;
    private final Set<String> cache = ConcurrentHashMap.newKeySet();

    public TokenRevocationService(RevokedTokenRepository repository, JwtUtil jwtUtil) {
        this.repository = repository;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank() || !jwtUtil.isTokenValid(token)) {
            return;
        }
        Claims claims = jwtUtil.parseClaims(token);
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            return;
        }
        Date exp = claims.getExpiration();
        Instant expiresAt = exp != null ? exp.toInstant() : Instant.now().plusSeconds(60L * 60L * 24L);
        if (expiresAt.isBefore(Instant.now())) {
            return;
        }
        repository.save(new RevokedToken(jti, expiresAt));
        cache.add(jti);
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank() || !jwtUtil.isTokenValid(token)) {
            return true;
        }
        String jti = jwtUtil.extractJti(token);
        if (jti == null || jti.isBlank()) {
            // Pre-jti tokens cannot be revoked individually — reject them.
            return true;
        }
        if (cache.contains(jti)) {
            return true;
        }
        if (repository.existsById(jti)) {
            cache.add(jti);
            return true;
        }
        return false;
    }

    @Transactional
    @Scheduled(fixedDelay = 60L * 60L * 1000L)
    public void purgeExpired() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }
}
