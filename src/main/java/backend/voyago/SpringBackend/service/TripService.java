package backend.voyago.SpringBackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.voyago.SpringBackend.dto.CreateTripRequest;
import backend.voyago.SpringBackend.dto.CreateTripPreference;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripItineraryDay;
import backend.voyago.SpringBackend.model.TripMember;
import backend.voyago.SpringBackend.model.TripPreferences;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripEventRepository;
import backend.voyago.SpringBackend.repository.TripInviteRepository;
import backend.voyago.SpringBackend.repository.TripItineraryDayRepository;
import backend.voyago.SpringBackend.repository.TripMemberRepository;
import backend.voyago.SpringBackend.repository.TripRepository;
import backend.voyago.SpringBackend.repository.TripRepositoryPerference;
import backend.voyago.SpringBackend.repository.UserRepository;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripRepositoryPerference tripPreferencesRepository;
    private final TripItineraryDayRepository dayRepository;
    private final TripEventRepository eventRepository;
    private final UserRepository userRepository;
    private final TripMemberRepository memberRepository;
    private final TripInviteRepository inviteRepository;
    private final PlacesPhotoService placesPhotoService;
    private final TripAccessService access;
    private final TripLimitService tripLimit;

    public TripService(TripRepository tripRepository,
                       TripRepositoryPerference tripPreferencesRepository,
                       TripItineraryDayRepository dayRepository,
                       TripEventRepository eventRepository,
                       UserRepository userRepository,
                       TripMemberRepository memberRepository,
                       TripInviteRepository inviteRepository,
                       PlacesPhotoService placesPhotoService,
                       TripAccessService access,
                       TripLimitService tripLimit)
    {
        this.tripRepository = tripRepository;
        this.tripPreferencesRepository = tripPreferencesRepository;
        this.dayRepository = dayRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.placesPhotoService = placesPhotoService;
        this.access = access;
        this.tripLimit = tripLimit;
    }

    // Create a new trip and link it to the logged-in user
    public Trip createTrip(CreateTripRequest request, String email)
    {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        tripLimit.assertCanAddTrip(user);

        Trip trip = new Trip();
        trip.setTitle(request.getTitle());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNumTravelers(request.getNumTravelers());
        String imageUrl = placesPhotoService.findPhotoUrl(request.getDestination());
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = request.getImageUrl();
        }
        trip.setImageUrl(imageUrl);
        trip.setStatus(TripAccessService.STATUS_PLANNING);
        trip.setCreatedAt(LocalDateTime.now());
        trip.setUser(user);

        Trip saved = tripRepository.save(trip);
        access.ensureAdminMembership(saved);
        return saved;
    }

    // Trips the user owns plus trips they accepted an invite to
    public List<Map<String, Object>> getTripsForUser(String email)
    {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<Long, Trip> trips = new LinkedHashMap<>();
        for (Trip owned : tripRepository.findByUser(user)) {
            trips.put(owned.getTid(), owned);
        }
        for (TripMember member : memberRepository.findByUserAndStatus(user, TripAccessService.MEMBER_ACCEPTED)) {
            Trip trip = member.getTrip();
            trips.putIfAbsent(trip.getTid(), trip);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Trip trip : trips.values()) {
            out.add(toPayload(trip, user));
        }
        return out;
    }

    public Trip getTripById(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
    }

    /** Trip plus the caller's role, so the UI knows what to lock. */
    public Map<String, Object> getTripForUser(Long tripId, String email) {
        Trip trip = access.requireMember(tripId, email);
        access.ensureAdminMembership(trip);
        return toPayload(trip, access.requireUser(email));
    }

    public Map<String, Object> setConfirmed(Long tripId, String email, boolean confirmed) {
        Trip trip = access.requireAdmin(tripId, email);
        trip.setStatus(confirmed ? TripAccessService.STATUS_CONFIRMED : TripAccessService.STATUS_PLANNING);
        Trip saved = tripRepository.save(trip);
        return toPayload(saved, access.requireUser(email));
    }

    // Delete a trip and all its dependent data
    @Transactional
    public void deleteTrip(Long tripId, String email)
    {
        Trip trip = access.requireAdmin(tripId, email);

        // 1. delete events → days → preferences → members/invites → trip (in FK order)
        List<TripItineraryDay> days = dayRepository.findByTripOrderByDayNumber(trip);
        for (TripItineraryDay day : days) {
            eventRepository.deleteAll(eventRepository.findByDayOrderByOrderIndex(day));
        }
        dayRepository.deleteAll(days);
        tripPreferencesRepository.findByTrip(trip).ifPresent(tripPreferencesRepository::delete);
        inviteRepository.deleteAll(inviteRepository.findByTrip(trip));
        memberRepository.deleteAll(memberRepository.findByTrip(trip));
        tripRepository.delete(trip);
    }

    // Save (or update) preferences for an existing trip
    public TripPreferences savePreferences(Long tripId, CreateTripPreference request, String email)
    {
        Trip trip = access.requireEditable(tripId, email);

        // Upsert: update existing row if present, otherwise create new
        TripPreferences prefs = tripPreferencesRepository.findByTrip(trip)
                .orElse(new TripPreferences());

        prefs.setTrip(trip);
        prefs.setCurrentLocation(request.getCurrentLocation());
        prefs.setTripType(request.getTripType());
        prefs.setAccommodation(request.getAccommodation());
        prefs.setTransportation(request.getTransportation());
        prefs.setInterests(request.getInterests());
        prefs.setNotes(request.getNotes());

        return tripPreferencesRepository.save(prefs);
    }

    private Map<String, Object> toPayload(Trip trip, User viewer) {
        String role = access.roleFor(trip, viewer);
        boolean isAdmin = TripAccessService.ROLE_ADMIN.equals(role);
        boolean confirmed = access.isConfirmed(trip);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tid", trip.getTid());
        payload.put("title", trip.getTitle());
        payload.put("destination", trip.getDestination());
        payload.put("startDate", trip.getStartDate() != null ? trip.getStartDate().toString() : null);
        payload.put("endDate", trip.getEndDate() != null ? trip.getEndDate().toString() : null);
        payload.put("numTravelers", trip.getNumTravelers());
        payload.put("imageUrl", trip.getImageUrl());
        payload.put("status", confirmed ? TripAccessService.STATUS_CONFIRMED : TripAccessService.STATUS_PLANNING);
        payload.put("createdAt", trip.getCreatedAt() != null ? trip.getCreatedAt().toString() : null);
        payload.put("role", role);
        payload.put("isAdmin", isAdmin);
        payload.put("confirmed", confirmed);
        payload.put("canEdit", role != null && !confirmed);
        return payload;
    }
}
