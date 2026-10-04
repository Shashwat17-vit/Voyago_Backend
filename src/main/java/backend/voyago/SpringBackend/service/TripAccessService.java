package backend.voyago.SpringBackend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripMember;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripMemberRepository;
import backend.voyago.SpringBackend.repository.TripRepository;
import backend.voyago.SpringBackend.repository.UserRepository;

/**
 * Single place that answers "may this user see / edit this trip?".
 * Membership comes from trip_member; the trip creator is always ADMIN.
 */
@Service
public class TripAccessService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";

    public static final String MEMBER_ACCEPTED = "ACCEPTED";

    public static final String STATUS_PLANNING = "PLANNING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private final TripRepository tripRepository;
    private final TripMemberRepository memberRepository;
    private final UserRepository userRepository;

    public TripAccessService(TripRepository tripRepository,
                             TripMemberRepository memberRepository,
                             UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    public User requireUser(String email) {
        return userRepository.findOneByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public Trip requireTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
    }

    public boolean isOwner(Trip trip, User user) {
        return trip.getUser() != null && trip.getUser().getUid().equals(user.getUid());
    }

    /** ADMIN / MEMBER / null when the user has no accepted access. */
    public String roleFor(Trip trip, User user) {
        if (isOwner(trip, user)) {
            return ROLE_ADMIN;
        }
        return memberRepository.findByTripAndUser(trip, user)
                .filter(m -> MEMBER_ACCEPTED.equalsIgnoreCase(m.getStatus()))
                .map(TripMember::getRole)
                .orElse(null);
    }

    /** Trips created before trip_member existed have no admin row — add it on demand. */
    public TripMember ensureAdminMembership(Trip trip) {
        User owner = trip.getUser();
        if (owner == null) {
            return null;
        }
        return memberRepository.findByTripAndUser(trip, owner).orElseGet(() -> {
            TripMember admin = new TripMember();
            admin.setTrip(trip);
            admin.setUser(owner);
            admin.setRole(ROLE_ADMIN);
            admin.setStatus(MEMBER_ACCEPTED);
            admin.setJoinedAt(trip.getCreatedAt() != null ? trip.getCreatedAt() : LocalDateTime.now());
            return memberRepository.save(admin);
        });
    }

    public Trip requireMember(Long tripId, String email) {
        Trip trip = requireTrip(tripId);
        User user = requireUser(email);
        if (roleFor(trip, user) == null) {
            throw new ForbiddenException("You do not have access to this trip");
        }
        return trip;
    }

    public Trip requireAdmin(Long tripId, String email) {
        Trip trip = requireTrip(tripId);
        requireAdmin(trip, email);
        return trip;
    }

    public void requireAdmin(Trip trip, String email) {
        User user = requireUser(email);
        if (!ROLE_ADMIN.equals(roleFor(trip, user))) {
            throw new ForbiddenException("Only the trip admin can do this");
        }
    }

    /** Member access plus the trip must still be in PLANNING. */
    public Trip requireEditable(Long tripId, String email) {
        Trip trip = requireMember(tripId, email);
        requireNotConfirmed(trip);
        return trip;
    }

    public void requireEditable(Trip trip, String email) {
        User user = requireUser(email);
        if (roleFor(trip, user) == null) {
            throw new ForbiddenException("You do not have access to this trip");
        }
        requireNotConfirmed(trip);
    }

    public void requireNotConfirmed(Trip trip) {
        if (isConfirmed(trip)) {
            throw new ForbiddenException(
                    "This trip is confirmed. The admin must unconfirm it before it can be changed.");
        }
    }

    public boolean isConfirmed(Trip trip) {
        return STATUS_CONFIRMED.equalsIgnoreCase(trip.getStatus());
    }
}
