package backend.voyago.SpringBackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.dto.CreateTripRequest;
import backend.voyago.SpringBackend.dto.CreateTripPreference;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripItineraryDay;
import backend.voyago.SpringBackend.model.TripPreferences;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripEventRepository;
import backend.voyago.SpringBackend.repository.TripItineraryDayRepository;
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

    public TripService(TripRepository tripRepository,
                       TripRepositoryPerference tripPreferencesRepository,
                       TripItineraryDayRepository dayRepository,
                       TripEventRepository eventRepository,
                       UserRepository userRepository)
    {
        this.tripRepository = tripRepository;
        this.tripPreferencesRepository = tripPreferencesRepository;
        this.dayRepository = dayRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    // Create a new trip and link it to the logged-in user
    public Trip createTrip(CreateTripRequest request, String email)
    {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Trip trip = new Trip();
        trip.setTitle(request.getTitle());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setNumTravelers(request.getNumTravelers());
        trip.setImageUrl(request.getImageUrl());
        trip.setStatus("PLANNING");
        trip.setCreatedAt(LocalDateTime.now());
        trip.setUser(user);

        return tripRepository.save(trip);
    }

    // Get all trips for the logged-in user
    public List<Trip> getTripsForUser(String email)
    {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return tripRepository.findByUser(user);
    }

    public Trip getTripById(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
    }

    // Delete a trip and all its dependent data
    public void deleteTrip(Long tripId)
    {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        // 1. delete events → days → preferences → trip (in FK order)
        List<TripItineraryDay> days = dayRepository.findByTripOrderByDayNumber(trip);
        for (TripItineraryDay day : days) {
            eventRepository.deleteAll(eventRepository.findByDayOrderByOrderIndex(day));
        }
        dayRepository.deleteAll(days);
        tripPreferencesRepository.findByTrip(trip).ifPresent(tripPreferencesRepository::delete);
        tripRepository.delete(trip);
    }

    // Save (or update) preferences for an existing trip
    public TripPreferences savePreferences(Long tripId, CreateTripPreference request)
    {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        // Upsert: update existing row if present, otherwise create new
        TripPreferences prefs = tripPreferencesRepository.findByTrip(trip)
                .orElse(new TripPreferences());

        prefs.setTrip(trip);
        prefs.setCurrentLocation(request.getCurrentLocation());
        prefs.setBudget(request.getBudget());
        prefs.setTripType(request.getTripType());
        prefs.setAccommodation(request.getAccommodation());
        prefs.setTransportation(request.getTransportation());
        prefs.setInterests(request.getInterests());
        prefs.setNotes(request.getNotes());

        return tripPreferencesRepository.save(prefs);
    }
}
