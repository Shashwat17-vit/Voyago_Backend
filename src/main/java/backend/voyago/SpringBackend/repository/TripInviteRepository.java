package backend.voyago.SpringBackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripInvite;

@Repository
public interface TripInviteRepository extends JpaRepository<TripInvite, Long> {

    Optional<TripInvite> findByToken(String token);

    List<TripInvite> findByTrip(Trip trip);

    List<TripInvite> findByTripAndStatus(Trip trip, String status);

    Optional<TripInvite> findByTripAndEmailAndStatus(Trip trip, String email, String status);

    List<TripInvite> findByEmailAndStatus(String email, String status);
}
