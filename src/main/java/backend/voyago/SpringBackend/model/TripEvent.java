package backend.voyago.SpringBackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "trip_event", schema = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private TripItineraryDay day;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_name")
    private String locationName;

    private Double latitude;
    private Double longitude;

    private String category; // FOOD / ACTIVITY / TRANSPORT / ACCOMMODATION / SIGHTSEEING

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "order_index")
    private Integer orderIndex;
}
