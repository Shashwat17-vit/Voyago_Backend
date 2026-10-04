package backend.voyago.SpringBackend.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import backend.voyago.SpringBackend.model.Trip;
import backend.voyago.SpringBackend.model.TripEvent;
import backend.voyago.SpringBackend.model.TripItineraryDay;
import backend.voyago.SpringBackend.model.TripPreferences;
import backend.voyago.SpringBackend.repository.TripEventRepository;
import backend.voyago.SpringBackend.repository.TripItineraryDayRepository;
import backend.voyago.SpringBackend.repository.TripRepositoryPerference;

@Service
public class ItineraryService {

    private final TripRepositoryPerference prefsRepository;
    private final TripItineraryDayRepository dayRepository;
    private final TripEventRepository eventRepository;
    private final TripAccessService access;
    private final RestTemplate restTemplate;

    @Value("${agent.url:http://localhost:8000}")
    private String agentUrl;

    public ItineraryService(TripRepositoryPerference prefsRepository,
                            TripItineraryDayRepository dayRepository,
                            TripEventRepository eventRepository,
                            TripAccessService access) {
        this.prefsRepository  = prefsRepository;
        this.dayRepository    = dayRepository;
        this.eventRepository  = eventRepository;
        this.access           = access;
        this.restTemplate     = new RestTemplate();
    }

    @Transactional
    public List<TripItineraryDay> generate(Long tripId, String email) {
        Trip trip = access.requireEditable(tripId, email);

        TripPreferences prefs = prefsRepository.findByTrip(trip)
                .orElseThrow(() -> new RuntimeException("Trip preferences not found — save preferences first"));

        Map<String, Object> body = Map.of(
            "trip", Map.of(
                "destination",   trip.getDestination(),
                "startDate",     trip.getStartDate().toString(),
                "endDate",       trip.getEndDate().toString(),
                "numTravelers",  trip.getNumTravelers()
            ),
            "preferences", Map.of(
                "currentLocation", prefs.getCurrentLocation(),
                "tripType",        prefs.getTripType(),
                "accommodation",   prefs.getAccommodation(),
                "transportation",  prefs.getTransportation(),
                "interests",       prefs.getInterests(),
                "notes",           prefs.getNotes() != null ? prefs.getNotes() : ""
            )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                agentUrl + "/generate-itinerary", request, Map.class);

        List<ParsedDay> parsed = parseAgentItinerary(response.getBody(), trip);
        if (parsed.isEmpty()) {
            throw new RuntimeException("The planner returned an empty itinerary. Your existing calendar was kept.");
        }

        List<TripItineraryDay> existing = dayRepository.findByTripOrderByDayNumber(trip);
        for (TripItineraryDay old : existing) {
            eventRepository.deleteAll(eventRepository.findByDayOrderByOrderIndex(old));
        }
        dayRepository.deleteAll(existing);
        dayRepository.flush();

        for (ParsedDay dayData : parsed) {
            TripItineraryDay day = new TripItineraryDay();
            day.setTrip(trip);
            day.setDayNumber(dayData.dayNumber);
            day.setDayLabel(dayData.dayLabel);
            day.setDate(dayData.date);
            TripItineraryDay savedDay = dayRepository.save(day);

            for (ParsedEvent eventData : dayData.events) {
                TripEvent event = new TripEvent();
                event.setDay(savedDay);
                event.setTitle(eventData.title);
                event.setDescription(eventData.description);
                event.setLocationName(eventData.locationName);
                event.setLatitude(eventData.latitude);
                event.setLongitude(eventData.longitude);
                event.setCategory(eventData.category);
                event.setStartTime(eventData.startTime);
                event.setEndTime(eventData.endTime);
                event.setOrderIndex(eventData.orderIndex);
                eventRepository.save(event);
            }
        }

        return dayRepository.findByTripOrderByDayNumber(trip);
    }

    public List<Map<String, Object>> getItinerary(Long tripId, String email) {
        Trip trip = access.requireMember(tripId, email);

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

    public Map<String, Object> createEvent(Long dayId, Map<String, Object> body, String email) {
        TripItineraryDay day = dayRepository.findById(dayId)
                .orElseThrow(() -> new RuntimeException("Day not found"));
        access.requireEditable(day.getTrip(), email);
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

    public void updateEvent(Long eventId, Map<String, Object> body, String email) {
        TripEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        access.requireEditable(event.getDay().getTrip(), email);
        if (body.containsKey("title"))        event.setTitle((String) body.get("title"));
        if (body.containsKey("description"))  event.setDescription((String) body.get("description"));
        if (body.containsKey("locationName")) event.setLocationName((String) body.get("locationName"));
        if (body.containsKey("category"))     event.setCategory(normalizeCategory((String) body.get("category")));
        if (body.containsKey("startTime"))    event.setStartTime(LocalTime.parse((String) body.get("startTime")));
        if (body.containsKey("endTime"))      event.setEndTime(LocalTime.parse((String) body.get("endTime")));
        eventRepository.save(event);
    }

    public void deleteEvent(Long eventId, String email) {
        TripEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        access.requireEditable(event.getDay().getTrip(), email);
        eventRepository.delete(event);
    }

    public Map<String, Object> duplicateEvent(Long eventId, String email) {
        TripEvent orig = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        access.requireEditable(orig.getDay().getTrip(), email);
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

    private List<ParsedDay> parseAgentItinerary(Map<String, Object> agentResult, Trip trip) {
        if (agentResult == null || !(agentResult.get("days") instanceof List<?> rawDays)) {
            throw new RuntimeException("The planner returned invalid itinerary data. Your existing calendar was kept.");
        }

        List<ParsedDay> parsed = new ArrayList<>();
        int fallbackDay = 1;
        for (Object rawDay : rawDays) {
            if (!(rawDay instanceof Map<?, ?> dayMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dayData = (Map<String, Object>) dayMap;

            int dayNumber = toInt(dayData.get("dayNumber"), fallbackDay);
            LocalDate date = parseDate(dayData.get("date"));
            if (date == null && trip.getStartDate() != null) {
                date = trip.getStartDate().plusDays(Math.max(0, dayNumber - 1));
            }
            if (date == null) {
                continue;
            }

            String label = stringify(dayData.get("dayLabel"));
            if (label.isBlank()) {
                label = "Day " + dayNumber;
            }

            List<ParsedEvent> events = new ArrayList<>();
            Object rawEvents = dayData.get("events");
            if (rawEvents instanceof List<?> eventList) {
                int fallbackOrder = 1;
                for (Object rawEvent : eventList) {
                    if (!(rawEvent instanceof Map<?, ?> eventMap)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> eventData = (Map<String, Object>) eventMap;
                    ParsedEvent event = parseEvent(eventData, fallbackOrder);
                    if (event != null) {
                        events.add(event);
                        fallbackOrder = event.orderIndex + 1;
                    }
                }
            }
            if (events.isEmpty()) {
                continue;
            }
            parsed.add(new ParsedDay(dayNumber, label, date, events));
            fallbackDay = dayNumber + 1;
        }
        return parsed;
    }

    private ParsedEvent parseEvent(Map<String, Object> eventData, int fallbackOrder) {
        String title = stringify(eventData.get("title"));
        if (title.isBlank()) {
            return null;
        }
        LocalTime start = parseTime(eventData.get("startTime"));
        if (start == null) {
            return null;
        }
        LocalTime end = parseTime(eventData.get("endTime"));
        if (end == null) {
            end = start.plusHours(1);
        }
        Double[] coords = coords(eventData.get("latitude"), eventData.get("longitude"));
        return new ParsedEvent(
                title,
                stringify(eventData.get("description")),
                stringify(eventData.get("locationName")),
                coords[0],
                coords[1],
                normalizeCategory(stringify(eventData.get("category"))),
                start,
                end,
                toInt(eventData.get("orderIndex"), fallbackOrder)
        );
    }

    private Double[] coords(Object latVal, Object lngVal) {
        Double lat = toDouble(latVal);
        Double lng = toDouble(lngVal);
        if (lat == null || lng == null) {
            return new Double[] { null, null };
        }
        if (Math.abs(lat) < 1e-9 && Math.abs(lng) < 1e-9) {
            return new Double[] { null, null };
        }
        return new Double[] { lat, lng };
    }

    private int toInt(Object val, int fallback) {
        if (val instanceof Number number) {
            return number.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(val.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.toString().trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        value = value.replaceAll("(?i)\\s*(am|pm)$", " $1").replaceAll("\\s+", " ").trim();
        DateTimeFormatter[] formats = {
                DateTimeFormatter.ISO_LOCAL_TIME,
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ofPattern("h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("h:mma", Locale.US)
        };
        for (DateTimeFormatter format : formats) {
            try {
                return LocalTime.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        return null;
    }

    private String stringify(Object val) {
        return val == null ? "" : val.toString().trim();
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParsedDay(int dayNumber, String dayLabel, LocalDate date, List<ParsedEvent> events) {}

    private record ParsedEvent(String title, String description, String locationName,
                               Double latitude, Double longitude, String category,
                               LocalTime startTime, LocalTime endTime, int orderIndex) {}
}
