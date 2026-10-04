package backend.voyago.SpringBackend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.service.ItineraryService;

@RestController
@RequestMapping("/api/trips")
public class ItineraryController {

    private final ItineraryService itineraryService;

    public ItineraryController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    // POST /api/trips/{id}/generate — call Python agent and save itinerary
    @PostMapping("/{tripId}/generate")
    public ResponseEntity<?> generate(@PathVariable Long tripId, Authentication authentication) {
        try {
            int dayCount = itineraryService.generate(tripId, CurrentUser.email(authentication)).size();
            return ResponseEntity.ok(Map.of(
                "message", "Itinerary generated",
                "days",    dayCount
            ));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trips/{id}/itinerary — fetch saved itinerary for frontend
    @GetMapping("/{tripId}/itinerary")
    public ResponseEntity<?> getItinerary(@PathVariable Long tripId, Authentication authentication) {
        try {
            return ResponseEntity.ok(itineraryService.getItinerary(tripId, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
