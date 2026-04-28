package backend.voyago.SpringBackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_member", schema = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tid", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uid", nullable = false)
    private User user;

    private String role;   // ADMIN / MEMBER

    private String status; // INVITED / ACCEPTED / DECLINED

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
