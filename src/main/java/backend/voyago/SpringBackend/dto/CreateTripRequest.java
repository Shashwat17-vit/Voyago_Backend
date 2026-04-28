package backend.voyago.SpringBackend.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class CreateTripRequest {
    private String title;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numTravelers;
    private String imageUrl;
}
