package backend.voyago.SpringBackend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_invite")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inviteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tid", nullable = false)
    private Trip trip;

    // Stored lowercase — the invited person may not have an account yet
    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    private String status; // PENDING / ACCEPTED / DECLINED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
