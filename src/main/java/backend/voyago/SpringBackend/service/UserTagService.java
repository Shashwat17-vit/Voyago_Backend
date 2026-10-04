package backend.voyago.SpringBackend.service;

import java.security.SecureRandom;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.UserRepository;

@Service
public class UserTagService {

    private static final Logger log = LoggerFactory.getLogger(UserTagService.class);
    // I, O, 0, 1 omitted so handles stay readable aloud.
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int DEFAULT_LENGTH = 4;
    private static final int WIDE_LENGTH = 5;
    private static final int MAX_ATTEMPTS = 24;

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public UserTagService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String assign(User user) {
        if (user.getTag() != null && !user.getTag().isBlank()) {
            return user.getTag();
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int length = attempt < 16 ? DEFAULT_LENGTH : WIDE_LENGTH;
            String tag = generate(length);
            if (userRepository.existsByTagIgnoreCase(tag)) {
                continue;
            }
            user.setTag(tag);
            try {
                userRepository.save(user);
                return tag;
            } catch (DataIntegrityViolationException e) {
                user.setTag(null);
            }
        }
        throw new RuntimeException("Could not assign a unique handle. Please try again.");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillMissingTags() {
        List<User> missing = userRepository.findByTagIsNull();
        if (missing.isEmpty()) {
            return;
        }
        int assigned = 0;
        for (User user : missing) {
            try {
                assign(user);
                assigned++;
            } catch (RuntimeException e) {
                log.warn("Could not assign a handle to user {}", user.getUid(), e);
            }
        }
        log.info("Assigned handles to {} existing user(s)", assigned);
    }

    private String generate(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
