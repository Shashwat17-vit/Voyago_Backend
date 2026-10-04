package backend.voyago.SpringBackend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import backend.voyago.SpringBackend.dto.CreateTripPreference;
import backend.voyago.SpringBackend.dto.CreateTripRequest;
import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripPreferences;
import backend.voyago.SpringBackend.service.InviteService;
import backend.voyago.SpringBackend.service.TripService;

@RestController
@RequestMapping("/api/trips")
public class NewTripController {

    private final TripService tripService;
    private final InviteService inviteService;

    public NewTripController(TripService tripService, InviteService inviteService)
    {
        this.tripService = tripService;
        this.inviteService = inviteService;
    }

    // POST /api/trips — create a new trip for the logged-in user
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTrip(
            @RequestBody CreateTripRequest request,
            Authentication authentication)
    {
        try {
            String email = CurrentUser.email(authentication);
            Trip trip = tripService.createTrip(request, email);
            return ResponseEntity.ok(Map.of("message", "Trip created", "tid", trip.getTid()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trips/{id} — trip plus the caller's role on it
    @GetMapping("/{tripId}")
    public ResponseEntity<?> getTrip(@PathVariable Long tripId, Authentication authentication) {
        try {
            return ResponseEntity.ok(tripService.getTripForUser(tripId, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trips — trips the user owns or has joined
    @GetMapping
    public ResponseEntity<?> getTrips(Authentication authentication)
    {
        try {
            String email = CurrentUser.email(authentication);
            return ResponseEntity.ok(tripService.getTripsForUser(email));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE /api/trips/{tripId} — admin only
    @DeleteMapping("/{tripId}")
    public ResponseEntity<Map<String, String>> deleteTrip(@PathVariable Long tripId,
                                                          Authentication authentication) {
        try {
            tripService.deleteTrip(tripId, CurrentUser.email(authentication));
            return ResponseEntity.ok(Map.of("message", "Trip deleted"));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/trips/{tripId}/preferences — save preferences for an existing trip
    @PostMapping("/{tripId}/preferences")
    public ResponseEntity<Map<String, Object>> savePreferences(
            @PathVariable Long tripId,
            @RequestBody CreateTripPreference request,
            Authentication authentication)
    {
        try {
            TripPreferences prefs = tripService.savePreferences(tripId, request, CurrentUser.email(authentication));
            return ResponseEntity.ok(Map.of("message", "Preferences saved", "prefId", prefs.getPrefId()));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/trips/{tripId}/confirm — admin locks the itinerary
    @PostMapping("/{tripId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long tripId, Authentication authentication) {
        return setConfirmed(tripId, authentication, true);
    }

    // POST /api/trips/{tripId}/unconfirm — admin reopens the trip for editing
    @PostMapping("/{tripId}/unconfirm")
    public ResponseEntity<?> unconfirm(@PathVariable Long tripId, Authentication authentication) {
        return setConfirmed(tripId, authentication, false);
    }

    // GET /api/trips/{tripId}/members — accepted members of the trip
    @GetMapping("/{tripId}/members")
    public ResponseEntity<?> members(@PathVariable Long tripId, Authentication authentication) {
        try {
            return ResponseEntity.ok(inviteService.members(tripId, CurrentUser.email(authentication)));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/trips/{tripId}/invites — pending invites (admin only)
    @GetMapping("/{tripId}/invites")
    public ResponseEntity<?> listInvites(@PathVariable Long tripId, Authentication authentication) {
        try {
            List<Map<String, Object>> invites =
                    inviteService.pendingForTrip(tripId, CurrentUser.email(authentication));
            return ResponseEntity.ok(invites);
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/trips/{tripId}/invites — admin invites an email address
    @PostMapping("/{tripId}/invites")
    public ResponseEntity<?> invite(@PathVariable Long tripId,
                                    @RequestBody Map<String, String> body,
                                    Authentication authentication) {
        try {
            Map<String, Object> invite = inviteService.invite(
                    tripId, body.get("email"), CurrentUser.email(authentication));
            return ResponseEntity.ok(invite);
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> setConfirmed(Long tripId, Authentication authentication, boolean confirmed) {
        try {
            return ResponseEntity.ok(
                    tripService.setConfirmed(tripId, CurrentUser.email(authentication), confirmed));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
