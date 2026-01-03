package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "AIRecommendations")
public class AIRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recId", nullable = false)
    private Integer id;

    @Column(name = "patientId", nullable = false)
    private Integer patientId;

    @Column(name = "clinicId", nullable = false)
    private Integer clinicId;

    @Column(name = "serviceId")
    private Integer serviceId;

    @Nationalized
    @Column(name = "reason", length = 400)
    private String reason;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "suggestedAt", nullable = false)
    private Instant suggestedAt;

    @Column(name = "accepted")
    private Boolean accepted;

    @Column(name = "actedAt")
    private Instant actedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getPatientId() {
        return patientId;
    }

    public void setPatientId(Integer patientId) {
        this.patientId = patientId;
    }

    public Integer getClinicId() {
        return clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Instant getSuggestedAt() {
        return suggestedAt;
    }

    public void setSuggestedAt(Instant suggestedAt) {
        this.suggestedAt = suggestedAt;
    }

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public Instant getActedAt() {
        return actedAt;
    }

    public void setActedAt(Instant actedAt) {
        this.actedAt = actedAt;
    }

}