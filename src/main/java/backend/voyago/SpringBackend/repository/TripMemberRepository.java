package backend.voyago.SpringBackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripMember;
import backend.voyago.SpringBackend.model.User;

@Repository
public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    List<TripMember> findByTrip(Trip trip);

    Optional<TripMember> findByTripAndUser(Trip trip, User user);

    List<TripMember> findByUserAndStatus(User user, String status);
}
