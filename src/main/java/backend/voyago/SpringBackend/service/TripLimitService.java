package backend.voyago.SpringBackend.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripMember;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripMemberRepository;
import backend.voyago.SpringBackend.repository.TripRepository;

/**
 * A person may be on at most {@link #MAX_TRIPS} trips they own or have joined
 * (Planning or Confirmed). Joining or creating another requires deleting one first.
 */
@Service
public class TripLimitService {

    public static final int MAX_TRIPS = 3;
    public static final String LIMIT_MESSAGE =
            "You're already part of 3 trips. Delete one before you create or accept another.";

    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;

    public TripLimitService(TripRepository tripRepository, TripMemberRepository memberRepository) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
    }

    public int countFor(User user) {
        Set<Long> ids = new HashSet<>();
        if (user == null) {
            return 0;
        }
        for (Trip trip : tripRepository.findByUser(user)) {
            if (trip.getTid() != null) {
                ids.add(trip.getTid());
            }
        }
        for (TripMember member : memberRepository.findByUserAndStatus(user, TripAccessService.MEMBER_ACCEPTED)) {
            if (member.getTrip() != null && member.getTrip().getTid() != null) {
                ids.add(member.getTrip().getTid());
            }
        }
        return ids.size();
    }

    public boolean hasRoom(User user) {
        return countFor(user) < MAX_TRIPS;
    }

    public boolean alreadyOnTrip(User user, Trip trip) {
        if (user == null || trip == null) {
            return false;
        }
        if (trip.getUser() != null && user.getUid().equals(trip.getUser().getUid())) {
            return true;
        }
        return memberRepository.findByTripAndUser(trip, user)
                .filter(m -> TripAccessService.MEMBER_ACCEPTED.equalsIgnoreCase(m.getStatus()))
                .isPresent();
    }

    public void assertCanAddTrip(User user) {
        if (!hasRoom(user)) {
            throw new RuntimeException(LIMIT_MESSAGE);
        }
    }
}
