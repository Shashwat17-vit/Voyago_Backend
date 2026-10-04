package backend.voyago.SpringBackend.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripMember;
import backend.voyago.SpringBackend.model.User;
import backend.voyago.SpringBackend.repository.TripMemberRepository;
import io.jsonwebtoken.Jwts;

/**
 * Voyago owns membership. Stream hosts the room. This service only mints a
 * user token and syncs accepted trip members onto {@code messaging:trip-{id}}.
 */
@Service
public class StreamChatService {

    private static final Logger log = LoggerFactory.getLogger(StreamChatService.class);
    private static final String STREAM_URL = "https://chat.stream-io-api.com";

    private final TripAccessService access;
    private final TripMemberRepository memberRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;
    private final String apiSecret;

    public StreamChatService(TripAccessService access,
                             TripMemberRepository memberRepository,
                             @Value("${stream.api-key:}") String apiKey,
                             @Value("${stream.api-secret:}") String apiSecret) {
        this.access = access;
        this.memberRepository = memberRepository;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    public Map<String, Object> tokenForTrip(Long tripId, String email) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Chat is not configured. Add STREAM_API_KEY and STREAM_API_SECRET.");
        }

        Trip trip = access.requireMember(tripId, email);
        access.ensureAdminMembership(trip);
        User caller = access.requireUser(email);

        List<User> members = acceptedMembers(trip);
        upsertUsers(members);
        String channelId = "trip-" + trip.getTid();
        syncChannel(channelId, trip, caller, members);

        Map<String, Object> out = new HashMap<>();
        out.put("apiKey", apiKey);
        out.put("token", userToken(streamUserId(caller)));
        out.put("userId", streamUserId(caller));
        out.put("userName", displayName(caller));
        out.put("channelId", channelId);
        out.put("channelType", "messaging");
        return out;
    }

    private List<User> acceptedMembers(Trip trip) {
        List<User> users = new ArrayList<>();
        for (TripMember member : memberRepository.findByTrip(trip)) {
            if (!TripAccessService.MEMBER_ACCEPTED.equalsIgnoreCase(member.getStatus())) {
                continue;
            }
            if (member.getUser() != null) {
                users.add(member.getUser());
            }
        }
        User owner = trip.getUser();
        if (owner != null && users.stream().noneMatch(u -> u.getUid().equals(owner.getUid()))) {
            users.add(owner);
        }
        return users;
    }

    private void upsertUsers(List<User> members) {
        Map<String, Object> users = new LinkedHashMap<>();
        for (User user : members) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", streamUserId(user));
            payload.put("name", displayName(user));
            users.put(streamUserId(user), payload);
        }
        post("/users", Map.of("users", users));
    }

    private void syncChannel(String channelId, Trip trip, User caller, List<User> members) {
        List<Map<String, String>> memberIds = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (User user : members) {
            String id = streamUserId(user);
            memberIds.add(Map.of("user_id", id));
            ids.add(id);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("created_by_id", streamUserId(caller));
        data.put("members", memberIds);
        data.put("name", trip.getTitle() != null ? trip.getTitle() : "Trip chat");
        data.put("trip_id", trip.getTid());

        post("/channels/messaging/" + channelId + "/query", Map.of(
                "data", data,
                "state", true,
                "watch", false,
                "presence", false
        ));

        if (!ids.isEmpty()) {
            try {
                post("/channels/messaging/" + channelId, Map.of("add_members", ids));
            } catch (RuntimeException e) {
                log.warn("Stream add_members for {} failed: {}", channelId, e.getMessage());
            }
        }
    }

    private void post(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", serverToken());
        headers.set("Stream-Auth-Type", "jwt");
        String url = STREAM_URL + path + "?api_key=" + apiKey;
        try {
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        } catch (RestClientResponseException e) {
            log.warn("Stream request {} failed: {} {}", path, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Stream Chat rejected the request (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            log.warn("Stream request {} failed: {}", path, e.getMessage());
            throw new RuntimeException("Could not reach Stream Chat");
        }
    }

    private String userToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("user_id", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60L * 60L * 24L)))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private String serverToken() {
        return Jwts.builder()
                .claim("server", true)
                .issuedAt(new Date())
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /** Stream only accepts HS256. A long secret would otherwise be signed as HS512 and return 401. */
    private SecretKey signingKey() {
        byte[] bytes = apiSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("STREAM_API_SECRET is too short");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    static String streamUserId(User user) {
        return String.valueOf(user.getUid());
    }

    static String displayName(User user) {
        if (user.getFull_name() != null && !user.getFull_name().isBlank()) {
            return user.getFull_name();
        }
        return user.getEmail() != null ? user.getEmail() : "Traveler";
    }
}
