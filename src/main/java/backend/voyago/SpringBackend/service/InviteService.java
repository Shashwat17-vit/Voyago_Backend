package backend.voyago.SpringBackend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import backend.voyago.SpringBackend.exception.ForbiddenException;
import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripInvite;
import backend.voyago.SpringBackend.model.TripMember;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripInviteRepository;
import backend.voyago.SpringBackend.repository.TripMemberRepository;
import backend.voyago.SpringBackend.repository.UserRepository;

@Service
public class InviteService {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final TripInviteRepository inviteRepository;
    private final TripMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TripAccessService access;
    private final TripLimitService tripLimit;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public InviteService(TripInviteRepository inviteRepository,
                         TripMemberRepository memberRepository,
                         UserRepository userRepository,
                         TripAccessService access,
                         TripLimitService tripLimit) {
        this.inviteRepository = inviteRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.tripLimit = tripLimit;
    }

    /**
     * Admin invite from either a handle ({@code tag}) or an email. Tag is resolved
     * to that user's email so the rest of the invite path stays unchanged.
     */
    public Map<String, Object> invite(Long tripId, String rawEmail, String rawTag, String inviterEmail) {
        String email = rawEmail;
        if (email == null || email.isBlank()) {
            email = resolveTag(rawTag);
        }
        return invite(tripId, email, inviterEmail);
    }

    /**
     * Admin invites an email address. Invites stay allowed while the trip is CONFIRMED —
     * joining a trip is not the same as editing its itinerary.
     */
    public Map<String, Object> invite(Long tripId, String rawEmail, String inviterEmail) {
        Trip trip = access.requireTrip(tripId);
        access.requireAdmin(trip, inviterEmail);
        access.ensureAdminMembership(trip);

        String email = normalize(rawEmail);
        if (email.isEmpty()) {
            throw new RuntimeException("Email address is required");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new RuntimeException("'" + rawEmail.trim() + "' is not a valid email address");
        }
        if (email.equalsIgnoreCase(normalize(inviterEmail))) {
            throw new RuntimeException("You are already on this trip");
        }

        Optional<User> existing = userRepository.findOneByEmail(email);
        if (existing.isPresent()) {
            boolean alreadyMember = memberRepository.findByTripAndUser(trip, existing.get())
                    .filter(m -> TripAccessService.MEMBER_ACCEPTED.equalsIgnoreCase(m.getStatus()))
                    .isPresent();
            if (alreadyMember || access.isOwner(trip, existing.get())) {
                throw new RuntimeException(email + " is already a member of this trip");
            }
        }

        // Re-use an open invite so the same link keeps working
        TripInvite invite = inviteRepository.findByTripAndEmailAndStatus(trip, email, PENDING)
                .orElseGet(() -> {
                    TripInvite fresh = new TripInvite();
                    fresh.setTrip(trip);
                    fresh.setEmail(email);
                    fresh.setToken(UUID.randomUUID().toString().replace("-", ""));
                    fresh.setStatus(PENDING);
                    fresh.setInvitedBy(access.requireUser(inviterEmail));
                    fresh.setCreatedAt(LocalDateTime.now());
                    return inviteRepository.save(fresh);
                });

        return invitePayload(invite, existing.isPresent());
    }

    /** Pending invites the admin has already sent for this trip. */
    public List<Map<String, Object>> pendingForTrip(Long tripId, String adminEmail) {
        Trip trip = access.requireTrip(tripId);
        access.requireAdmin(trip, adminEmail);

        List<Map<String, Object>> out = new ArrayList<>();
        for (TripInvite invite : inviteRepository.findByTripAndStatus(trip, PENDING)) {
            out.add(invitePayload(invite, userRepository.existsByEmailIgnoreCase(invite.getEmail())));
        }
        return out;
    }

    /** In-app inbox: invites waiting for the logged-in user. */
    public List<Map<String, Object>> pendingForUser(String email) {
        List<Map<String, Object>> out = new ArrayList<>();
        User viewer = userRepository.findOneByEmail(normalize(email)).orElse(null);
        for (TripInvite invite : inviteRepository.findByEmailAndStatus(normalize(email), PENDING)) {
            Map<String, Object> payload = invitePreview(invite);
            if (viewer != null) {
                putLimitFields(payload, viewer, invite.getTrip());
            }
            out.add(payload);
        }
        return out;
    }

    /** Detail shown on the /invite/:token page. */
    public Map<String, Object> preview(String token, String viewerEmail) {
        TripInvite invite = requireInvite(token);
        Map<String, Object> payload = invitePreview(invite);
        payload.put("emailMatches", invite.getEmail().equalsIgnoreCase(normalize(viewerEmail)));
        payload.put("viewerEmail", normalize(viewerEmail));
        userRepository.findOneByEmail(normalize(viewerEmail)).ifPresent(viewer ->
                putLimitFields(payload, viewer, invite.getTrip()));
        return payload;
    }

    public Map<String, Object> accept(String token, String viewerEmail) {
        TripInvite invite = requireInvite(token);
        requirePending(invite);
        requireSameEmail(invite, viewerEmail);

        Trip trip = invite.getTrip();
        User user = access.requireUser(normalize(viewerEmail));
        if (!tripLimit.alreadyOnTrip(user, trip)) {
            tripLimit.assertCanAddTrip(user);
        }

        TripMember member = memberRepository.findByTripAndUser(trip, user).orElseGet(TripMember::new);
        member.setTrip(trip);
        member.setUser(user);
        if (member.getRole() == null) {
            member.setRole(TripAccessService.ROLE_MEMBER);
        }
        member.setStatus(TripAccessService.MEMBER_ACCEPTED);
        member.setJoinedAt(LocalDateTime.now());
        memberRepository.save(member);

        invite.setStatus(ACCEPTED);
        invite.setRespondedAt(LocalDateTime.now());
        inviteRepository.save(invite);

        return Map.of(
                "message", "Invite accepted",
                "tripId", trip.getTid(),
                "tripTitle", trip.getTitle() != null ? trip.getTitle() : "");
    }

    public Map<String, Object> decline(String token, String viewerEmail) {
        TripInvite invite = requireInvite(token);
        requirePending(invite);
        requireSameEmail(invite, viewerEmail);

        invite.setStatus(DECLINED);
        invite.setRespondedAt(LocalDateTime.now());
        inviteRepository.save(invite);

        return Map.of("message", "Invite declined");
    }

    /** Everyone with accepted access, for the trip sidebar. */
    public List<Map<String, Object>> members(Long tripId, String viewerEmail) {
        Trip trip = access.requireMember(tripId, viewerEmail);
        access.ensureAdminMembership(trip);

        List<Map<String, Object>> out = new ArrayList<>();
        for (TripMember member : memberRepository.findByTrip(trip)) {
            if (!TripAccessService.MEMBER_ACCEPTED.equalsIgnoreCase(member.getStatus())) {
                continue;
            }
            User user = member.getUser();
            Map<String, Object> entry = new HashMap<>();
            entry.put("uid", user.getUid());
            entry.put("name", user.getFull_name() != null && !user.getFull_name().isBlank()
                    ? user.getFull_name() : user.getEmail());
            entry.put("email", user.getEmail());
            entry.put("tag", user.getTag());
            entry.put("handle", handleOf(user));
            entry.put("role", member.getRole());
            entry.put("isAdmin", TripAccessService.ROLE_ADMIN.equals(member.getRole()));
            out.add(entry);
        }
        // Admin first, then alphabetical
        out.sort((a, b) -> {
            boolean adminA = Boolean.TRUE.equals(a.get("isAdmin"));
            boolean adminB = Boolean.TRUE.equals(b.get("isAdmin"));
            if (adminA != adminB) return adminA ? -1 : 1;
            return String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name")));
        });
        return out;
    }

    private TripInvite requireInvite(String token) {
        return inviteRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("This invite link is not valid"));
    }

    private void requirePending(TripInvite invite) {
        if (!PENDING.equalsIgnoreCase(invite.getStatus())) {
            throw new RuntimeException("This invite has already been answered");
        }
    }

    private void requireSameEmail(TripInvite invite, String viewerEmail) {
        if (!invite.getEmail().equalsIgnoreCase(normalize(viewerEmail))) {
            throw new ForbiddenException(
                    "This invite was sent to a different email address. Log in with that account to respond.");
        }
    }

    private Map<String, Object> invitePayload(TripInvite invite, boolean existingUser) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("inviteId", invite.getInviteId());
        payload.put("email", invite.getEmail());
        payload.put("status", invite.getStatus());
        payload.put("existingUser", existingUser);
        payload.put("inviteUrl", inviteUrl(invite));
        if (existingUser) {
            userRepository.findOneByEmail(invite.getEmail()).ifPresent(user -> {
                payload.put("tag", user.getTag());
                payload.put("name", displayName(user));
                payload.put("handle", handleOf(user));
            });
        }
        return payload;
    }

    private Map<String, Object> invitePreview(TripInvite invite) {
        Trip trip = invite.getTrip();
        User inviter = invite.getInvitedBy();
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", invite.getToken());
        payload.put("email", invite.getEmail());
        payload.put("status", invite.getStatus());
        payload.put("tripId", trip.getTid());
        payload.put("tripTitle", trip.getTitle());
        payload.put("destination", trip.getDestination());
        payload.put("startDate", trip.getStartDate() != null ? trip.getStartDate().toString() : null);
        payload.put("endDate", trip.getEndDate() != null ? trip.getEndDate().toString() : null);
        payload.put("imageUrl", trip.getImageUrl());
        payload.put("invitedBy", inviter != null
                ? (inviter.getFull_name() != null && !inviter.getFull_name().isBlank()
                    ? inviter.getFull_name() : inviter.getEmail())
                : "");
        payload.put("inviteUrl", inviteUrl(invite));
        return payload;
    }

    private void putLimitFields(Map<String, Object> payload, User viewer, Trip trip) {
        int count = tripLimit.countFor(viewer);
        boolean alreadyOn = tripLimit.alreadyOnTrip(viewer, trip);
        boolean canAccept = alreadyOn || count < TripLimitService.MAX_TRIPS;
        payload.put("tripCount", count);
        payload.put("tripLimit", TripLimitService.MAX_TRIPS);
        payload.put("canAccept", canAccept);
        if (!canAccept) {
            payload.put("limitMessage", TripLimitService.LIMIT_MESSAGE);
        }
    }

    private String inviteUrl(TripInvite invite) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invite/" + invite.getToken();
    }

    private String resolveTag(String rawTag) {
        String tag = rawTag == null ? "" : rawTag.trim().replaceFirst("^#", "").toUpperCase();
        if (tag.isEmpty()) {
            throw new RuntimeException("Email address or user handle is required");
        }
        return userRepository.findByTagIgnoreCase(tag)
                .map(User::getEmail)
                .orElseThrow(() -> new RuntimeException("No Voyago account uses #" + tag));
    }

    private String displayName(User user) {
        return user.getFull_name() != null && !user.getFull_name().isBlank()
                ? user.getFull_name() : user.getEmail();
    }

    public static String handleOf(User user) {
        if (user == null || user.getTag() == null || user.getTag().isBlank()) {
            return "";
        }
        return "#" + user.getTag();
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
