package backend.voyago.SpringBackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.dto.CreateTripRequest;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripRepository;
import backend.voyago.SpringBackend.repository.UserRepository;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public TripService(TripRepository tripRepository, UserRepository userRepository)
    {
        this.tripRepository = tripRepository;
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
}
