package backend.voyago.SpringBackend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.service.InviteService;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    // GET /api/invites/pending — in-app inbox for the signed-in user
    @GetMapping("/pending")
    public ResponseEntity<?> pending(Authentication authentication) {
        try {
            List<Map<String, Object>> invites = inviteService.pendingForUser(CurrentUser.email(authentication));
            return ResponseEntity.ok(invites);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/invites/{token} — details shown on the accept page
    @GetMapping("/{token}")
    public ResponseEntity<?> preview(@PathVariable String token, Authentication authentication) {
        try {
            return ResponseEntity.ok(inviteService.preview(token, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<?> accept(@PathVariable String token, Authentication authentication) {
        try {
            return ResponseEntity.ok(inviteService.accept(token, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/decline")
    public ResponseEntity<?> decline(@PathVariable String token, Authentication authentication) {
        try {
            return ResponseEntity.ok(inviteService.decline(token, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
