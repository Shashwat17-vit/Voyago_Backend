package backend.voyago.SpringBackend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripPreferences;

@Repository
public interface TripRepositoryPerference extends JpaRepository<TripPreferences, Long> {

    Optional<TripPreferences> findByTrip(Trip trip);
}
