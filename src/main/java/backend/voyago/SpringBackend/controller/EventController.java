package backend.voyago.SpringBackend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.service.ItineraryService;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final ItineraryService itineraryService;

    public EventController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @PostMapping("/day/{dayId}")
    public ResponseEntity<?> createEvent(@PathVariable Long dayId,
                                         @RequestBody Map<String, Object> body,
                                         Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    itineraryService.createEvent(dayId, body, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<?> updateEvent(@PathVariable Long eventId,
                                         @RequestBody Map<String, Object> body,
                                         Authentication authentication) {
        try {
            itineraryService.updateEvent(eventId, body, CurrentUser.email(authentication));
            return ResponseEntity.ok(Map.of("message", "Event updated"));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> deleteEvent(@PathVariable Long eventId, Authentication authentication) {
        try {
            itineraryService.deleteEvent(eventId, CurrentUser.email(authentication));
            return ResponseEntity.ok(Map.of("message", "Event deleted"));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/duplicate")
    public ResponseEntity<?> duplicateEvent(@PathVariable Long eventId, Authentication authentication) {
        try {
            return ResponseEntity.ok(
                    itineraryService.duplicateEvent(eventId, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
