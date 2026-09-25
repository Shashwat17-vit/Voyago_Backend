package backend.voyago.SpringBackend.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PlacesPhotoService {

    private static final Logger log = LoggerFactory.getLogger(PlacesPhotoService.class);
    private static final String SEARCH_URL = "https://places.googleapis.com/v1/places:searchText";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;

    public PlacesPhotoService(@Value("${google.places.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /**
     * Returns a Google-hosted photo URL for the destination, or null if unavailable.
     * Uses Places API (New) Text Search + Place Photos (skipHttpRedirect).
     */
    public String findPhotoUrl(String destination) {
        if (apiKey.isEmpty() || destination == null || destination.isBlank()) {
            if (apiKey.isEmpty()) {
                log.warn("GOOGLE_PLACES_API_KEY is not set — trip images will use the frontend fallback");
            }
            return null;
        }

        try {
            String photoName = findFirstPhotoName(destination.trim());
            if (photoName == null || photoName.isBlank()) {
                return null;
            }
            return fetchPhotoUri(photoName);
        } catch (Exception e) {
            log.warn("Places photo lookup failed for '{}': {}", destination, e.getMessage());
            return null;
        }
    }

    private String findFirstPhotoName(String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "places.photos.name");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of("textQuery", destination, "maxResultCount", 1),
                headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(SEARCH_URL, request, Map.class);
        Object placesObj = response.getBody() == null ? null : response.getBody().get("places");
        if (!(placesObj instanceof java.util.List<?> places) || places.isEmpty()) {
            return null;
        }
        if (!(places.get(0) instanceof Map<?, ?> place)) {
            return null;
        }
        Object photosObj = place.get("photos");
        if (!(photosObj instanceof java.util.List<?> photos) || photos.isEmpty()) {
            return null;
        }
        if (!(photos.get(0) instanceof Map<?, ?> photo)) {
            return null;
        }
        Object name = photo.get("name");
        return name == null ? null : name.toString();
    }

    private String fetchPhotoUri(String photoName) {
        String url = "https://places.googleapis.com/v1/" + photoName
                + "/media?maxWidthPx=1200&skipHttpRedirect=true";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Goog-Api-Key", apiKey);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        if (response.getBody() == null) {
            return null;
        }
        Object uri = response.getBody().get("photoUri");
        return uri == null ? null : uri.toString();
    }
}
