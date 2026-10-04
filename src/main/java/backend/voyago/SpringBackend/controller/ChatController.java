package backend.voyago.SpringBackend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.service.StreamChatService;

@RestController
@RequestMapping("/api/trips")
public class ChatController {

    private final StreamChatService streamChatService;

    public ChatController(StreamChatService streamChatService) {
        this.streamChatService = streamChatService;
    }

    @GetMapping("/{tripId}/chat/token")
    public ResponseEntity<?> token(@PathVariable Long tripId, Authentication authentication) {
        try {
            return ResponseEntity.ok(streamChatService.tokenForTrip(tripId, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
