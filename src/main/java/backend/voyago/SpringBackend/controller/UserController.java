package backend.voyago.SpringBackend.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.UserRepository;
import backend.voyago.SpringBackend.service.InviteService;
import backend.voyago.SpringBackend.service.TripAccessService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final int MIN_QUERY = 2;
    private static final int MAX_RESULTS = 10;

    private final UserRepository userRepository;
    private final TripAccessService access;

    public UserController(UserRepository userRepository, TripAccessService access) {
        this.userRepository = userRepository;
        this.access = access;
    }

    // GET /api/users/search?q= — username and handle only; never returns email
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(value = "q", required = false) String raw,
                                    Authentication authentication) {
        try {
            String q = raw == null ? "" : raw.trim().replaceFirst("^#", "");
            if (q.length() < MIN_QUERY) {
                return ResponseEntity.ok(List.of());
            }
            User self = access.requireUser(CurrentUser.email(authentication));
            List<User> found = userRepository.searchByNameOrTag(
                    q, self.getUid(), PageRequest.of(0, MAX_RESULTS));
            List<Map<String, Object>> out = new ArrayList<>();
            for (User user : found) {
                Map<String, Object> row = new HashMap<>();
                row.put("uid", user.getUid());
                row.put("name", user.getFull_name() != null && !user.getFull_name().isBlank()
                        ? user.getFull_name() : "Voyago user");
                row.put("tag", user.getTag());
                row.put("handle", InviteService.handleOf(user));
                out.add(row);
            }
            return ResponseEntity.ok(out);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
