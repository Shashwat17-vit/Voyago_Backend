package backend.voyago.SpringBackend.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripEvent;
import backend.voyago.SpringBackend.model.TripItineraryDay;
import backend.voyago.SpringBackend.model.TripPreferences;
import backend.voyago.SpringBackend.repository.TripEventRepository;
import backend.voyago.SpringBackend.repository.TripItineraryDayRepository;
import backend.voyago.SpringBackend.repository.TripRepository;
import backend.voyago.SpringBackend.repository.TripRepositoryPerference;

@Service
public class ItineraryService {

    private final TripRepository tripRepository;
    private final TripRepositoryPerference prefsRepository;
    private final TripItineraryDayRepository dayRepository;
    private final TripEventRepository eventRepository;
    private final RestTemplate restTemplate;

    @Value("${agent.url:http://localhost:8000}")
    private String agentUrl;

    public ItineraryService(TripRepository tripRepository,
                            TripRepositoryPerference prefsRepository,
                            TripItineraryDayRepository dayRepository,
                            TripEventRepository eventRepository) {
        this.tripRepository   = tripRepository;
        this.prefsRepository  = prefsRepository;
        this.dayRepository    = dayRepository;
        this.eventRepository  = eventRepository;
        this.restTemplate     = new RestTemplate();
    }

    public List<TripItineraryDay> generate(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        TripPreferences prefs = prefsRepository.findByTrip(trip)
                .orElseThrow(() -> new RuntimeException("Trip preferences not found — save preferences first"));

        // Build request body for Python agent
        Map<String, Object> body = Map.of(
            "trip", Map.of(
                "destination",   trip.getDestination(),
                "startDate",     trip.getStartDate().toString(),
                "endDate",       trip.getEndDate().toString(),
                "numTravelers",  trip.getNumTravelers()
            ),
            "preferences", Map.of(
                "currentLocation", prefs.getCurrentLocation(),
                "budget",          prefs.getBudget(),
                "tripType",        prefs.getTripType(),
                "accommodation",   prefs.getAccommodation(),
                "transportation",  prefs.getTransportation(),
                "interests",       prefs.getInterests(),
                "notes",           prefs.getNotes() != null ? prefs.getNotes() : ""
            )
        );

        // Call Python agent — explicit JSON header so FastAPI receives application/json
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                agentUrl + "/generate-itinerary", request, Map.class);

        Map<String, Object> agentResult = response.getBody();

        // Delete previous itinerary for this trip if regenerating
        List<TripItineraryDay> existing = dayRepository.findByTripOrderByDayNumber(trip);
        for (TripItineraryDay old : existing) {
            eventRepository.deleteAll(eventRepository.findByDayOrderByOrderIndex(old));
        }
        dayRepository.deleteAll(existing);

        // Parse and save new itinerary
        List<Map<String, Object>> days = (List<Map<String, Object>>) agentResult.get("days");
        for (Map<String, Object> dayData : days) {
            TripItineraryDay day = new TripItineraryDay();
            day.setTrip(trip);
            day.setDayNumber((Integer) dayData.get("dayNumber"));
            day.setDayLabel((String) dayData.get("dayLabel"));
            day.setDate(LocalDate.parse((String) dayData.get("date")));
            TripItineraryDay savedDay = dayRepository.save(day);

            List<Map<String, Object>> events = (List<Map<String, Object>>) dayData.get("events");
            for (Map<String, Object> eventData : events) {
                TripEvent event = new TripEvent();
                event.setDay(savedDay);
                event.setTitle((String) eventData.get("title"));
                event.setDescription((String) eventData.get("description"));
                event.setLocationName((String) eventData.get("locationName"));
                event.setLatitude(toDouble(eventData.get("latitude")));
                event.setLongitude(toDouble(eventData.get("longitude")));
                event.setCategory((String) eventData.get("category"));
                event.setStartTime(LocalTime.parse((String) eventData.get("startTime")));
                event.setEndTime(LocalTime.parse((String) eventData.get("endTime")));
                event.setOrderIndex((Integer) eventData.get("orderIndex"));
                eventRepository.save(event);
            }
        }

        return dayRepository.findByTripOrderByDayNumber(trip);
    }

    public List<Map<String, Object>> getItinerary(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        return dayRepository.findByTripOrderByDayNumber(trip).stream().map(day -> {
            List<Map<String, Object>> events = eventRepository
                    .findByDayOrderByOrderIndex(day).stream().map(e -> {
                        Map<String, Object> ev = new HashMap<>();
                        ev.put("eventId",      e.getEventId());
                        ev.put("title",        e.getTitle());
                        ev.put("description",  e.getDescription());
                        ev.put("locationName", e.getLocationName());
                        ev.put("latitude",     e.getLatitude());
                        ev.put("longitude",    e.getLongitude());
                        ev.put("category",     e.getCategory());
                        ev.put("startTime",    e.getStartTime() != null ? e.getStartTime().toString() : null);
                        ev.put("endTime",      e.getEndTime()   != null ? e.getEndTime().toString()   : null);
                        ev.put("orderIndex",   e.getOrderIndex());
                        return ev;
                    }).collect(Collectors.toList());

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("dayId",     day.getDayId());
            dayMap.put("dayNumber", day.getDayNumber());
            dayMap.put("dayLabel",  day.getDayLabel());
            dayMap.put("date",      day.getDate().toString());
            dayMap.put("events",    events);
            return dayMap;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> createEvent(Long dayId, Map<String, Object> body) {
        TripItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException("Day not found"));
        TripEvent event = new TripEvent();
        event.setDay(day);
        event.setTitle((String) body.getOrDefault("title", "New Event"));
        event.setDescription((String) body.getOrDefault("description", ""));
        event.setLocationName((String) body.getOrDefault("locationName", ""));
        event.setCategory(normalizeCategory((String) body.getOrDefault("category", "SIGHTSEEING")));
        if (body.containsKey("startTime")) event.setStartTime(LocalTime.parse((String) body.get("startTime")));
        if (body.containsKey("endTime"))   event.setEndTime(LocalTime.parse((String) body.get("endTime")));
        List<TripEvent> existing = eventRepository.findByDayOrderByOrderIndex(day);
        event.setOrderIndex(existing.size());
        TripEvent saved = eventRepository.save(event);
        Map<String, Object> ev = new HashMap<>();
        ev.put("eventId",      saved.getEventId());
        ev.put("title",        saved.getTitle());
        ev.put("description",  saved.getDescription());
        ev.put("locationName", saved.getLocationName());
        ev.put("category",     saved.getCategory());
        ev.put("startTime",    saved.getStartTime() != null ? saved.getStartTime().toString() : null);
        ev.put("endTime",      saved.getEndTime()   != null ? saved.getEndTime().toString()   : null);
        ev.put("orderIndex",   saved.getOrderIndex());
        return ev;
    }

    public void updateEvent(Long eventId, Map<String, Object> body) {
        TripEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        if (body.containsKey("title"))        event.setTitle((String) body.get("title"));
        if (body.containsKey("description"))  event.setDescription((String) body.get("description"));
        if (body.containsKey("locationName")) event.setLocationName((String) body.get("locationName"));
        if (body.containsKey("category"))     event.setCategory(normalizeCategory((String) body.get("category")));
        if (body.containsKey("startTime"))    event.setStartTime(LocalTime.parse((String) body.get("startTime")));
        if (body.containsKey("endTime"))      event.setEndTime(LocalTime.parse((String) body.get("endTime")));
        eventRepository.save(event);
    }

    public void deleteEvent(Long eventId) {
        eventRepository.deleteById(eventId);
    }

    public Map<String, Object> duplicateEvent(Long eventId) {
        TripEvent orig = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        TripEvent copy = new TripEvent();
        copy.setDay(orig.getDay());
        copy.setTitle(orig.getTitle() + " (Copy)");
        copy.setDescription(orig.getDescription());
        copy.setLocationName(orig.getLocationName());
        copy.setLatitude(orig.getLatitude());
        copy.setLongitude(orig.getLongitude());
        copy.setCategory(orig.getCategory());
        LocalTime newStart = orig.getEndTime() != null ? orig.getEndTime() : orig.getStartTime().plusHours(1);
        copy.setStartTime(newStart);
        copy.setEndTime(newStart.plusHours(1));
        copy.setOrderIndex(orig.getOrderIndex() + 1);
        TripEvent saved = eventRepository.save(copy);
        Map<String, Object> ev = new HashMap<>();
        ev.put("eventId",      saved.getEventId());
        ev.put("title",        saved.getTitle());
        ev.put("description",  saved.getDescription());
        ev.put("locationName", saved.getLocationName());
        ev.put("category",     saved.getCategory());
        ev.put("startTime",    saved.getStartTime() != null ? saved.getStartTime().toString() : null);
        ev.put("endTime",      saved.getEndTime()   != null ? saved.getEndTime().toString()   : null);
        ev.put("orderIndex",   saved.getOrderIndex());
        return ev;
    }

    private String normalizeCategory(String raw) {
        if (raw == null) return "SIGHTSEEING";
        switch (raw.toUpperCase()) {
            case "FOOD":           return "FOOD";
            case "ACTIVITY":       return "ACTIVITY";
            case "TRANSPORT":
            case "FLIGHT":         return "TRANSPORT";
            case "ACCOMMODATION":
            case "STAY":           return "ACCOMMODATION";
            case "SIGHTSEEING":    return "SIGHTSEEING";
            default:               return "SIGHTSEEING";
        }
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Double) return (Double) val;
        if (val instanceof Integer) return ((Integer) val).doubleValue();
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }
}
