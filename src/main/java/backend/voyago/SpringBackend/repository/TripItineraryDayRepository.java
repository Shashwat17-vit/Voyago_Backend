package backend.voyago.SpringBackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripItineraryDay;

@Repository
public interface TripItineraryDayRepository extends JpaRepository<TripItineraryDay, Long> {
    List<TripItineraryDay> findByTripOrderByDayNumber(Trip trip);
}
