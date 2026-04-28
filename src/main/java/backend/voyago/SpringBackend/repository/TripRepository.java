package backend.voyago.SpringBackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.User;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    // Get all trips belonging to a specific user
    List<Trip> findByUser(User user);
}
