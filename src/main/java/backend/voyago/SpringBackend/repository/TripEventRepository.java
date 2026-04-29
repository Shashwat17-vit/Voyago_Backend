package backend.voyago.SpringBackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.TripEvent;
import backend.voyago.SpringBackend.model.TripItineraryDay;

@Repository
public interface TripEventRepository extends JpaRepository<TripEvent, Long> {
    List<TripEvent> findByDayOrderByOrderIndex(TripItineraryDay day);
}
