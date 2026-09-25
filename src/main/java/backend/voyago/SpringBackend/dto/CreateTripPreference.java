package backend.voyago.SpringBackend.dto;

import lombok.Data;

@Data
public class CreateTripPreference {
    private String currentLocation;
    private String tripType;
    private String accommodation;
    private String transportation;
    private String interests;
    private String notes;
}
