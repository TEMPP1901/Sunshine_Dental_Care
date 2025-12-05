package sunshine_dental_care.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FaceProfileUpdateRequests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceProfileUpdateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requestId")
    private Integer requestId;

    @Column(name = "userId", nullable = false)
    private Integer userId; // FK to User

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "doctorSpecialties", "userRoles",
            "hibernateLazyInitializer", "handler" })
    private User user;

    @Column(name = "newFaceEmbedding", columnDefinition = "NVARCHAR(MAX)")
    private String newFaceEmbedding; // JSON array of 512 floats: "[0.123, 0.456, ...]"

    @Column(name = "newFaceImageUrl", length = 400)
    private String newFaceImageUrl; // URL of new face image

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestStatus status; // PENDING, APPROVED, REJECTED

    @Column(name = "requestedAt", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewedAt")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewedBy")
    private Integer reviewedBy; // HR user ID who reviewed

    @Column(name = "rejectionReason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason; // Lý do từ chối (nếu có)

    public enum RequestStatus {
        PENDING, // Đang chờ duyệt
        APPROVED, // Đã duyệt
        REJECTED // Đã từ chối
    }
}
