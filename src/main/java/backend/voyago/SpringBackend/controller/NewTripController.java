package backend.voyago.SpringBackend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.oauth2.core.user.OAuth2User;

import backend.voyago.SpringBackend.dto.CreateTripRequest;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.service.TripService;

@RestController
@RequestMapping("/api/trips")
public class NewTripController {

    private final TripService tripService;

    public NewTripController(TripService tripService)
    {
        this.tripService = tripService;
    }

    // POST /api/trips — create a new trip for the logged-in user
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTrip(
            @RequestBody CreateTripRequest request,
            Authentication authentication)
    {
        try {
            String email = extractEmail(authentication);
            Trip trip = tripService.createTrip(request, email);
            return ResponseEntity.ok(Map.of("message", "Trip created", "tid", trip.getTid()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trips — get all trips for the logged-in user
    @GetMapping
    public ResponseEntity<List<Trip>> getTrips(Authentication authentication)
    {
        try {
            String email = extractEmail(authentication);
            List<Trip> trips = tripService.getTripsForUser(email);
            return ResponseEntity.ok(trips);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // JWT login sets principal as a String (email)
    // OAuth2 login sets principal as an OAuth2User object — need to extract email from attributes
    private String extractEmail(Authentication authentication)
    {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User)
        {
            return oAuth2User.getAttribute("email");
        }
        return (String) authentication.getPrincipal();
    }
}
