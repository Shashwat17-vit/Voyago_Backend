package backend.voyago.SpringBackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_preferences", schema = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long prefId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tid", nullable = false, unique = true)
    private Trip trip;

    @Column(name = "current_location")
    private String currentLocation;

    private String budget;

    @Column(name = "trip_type")
    private String tripType;

    private String accommodation;
    private String transportation;

    @Column(columnDefinition = "TEXT")
    private String interests;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
