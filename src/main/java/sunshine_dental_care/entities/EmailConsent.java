package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(name = "EmailConsents")
public class EmailConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patientId", nullable = false)
    private Patient patient;

    @Column(name = "isOptIn", nullable = false)
    private Boolean isOptIn = false;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "capturedAt", nullable = false)
    private Instant capturedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capturedBy")
    private User capturedBy;

    @Nationalized
    @Column(name = "proofUrl", length = 400)
    private String proofUrl; 

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Boolean getIsOptIn() {
        return isOptIn;
    }

    public void setIsOptIn(Boolean isOptIn) {
        this.isOptIn = isOptIn;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public User getCapturedBy() {
        return capturedBy;
    }

    public void setCapturedBy(User capturedBy) {
        this.capturedBy = capturedBy;
    }

    public String getProofUrl() {
        return proofUrl;
    }

    public void setProofUrl(String proofUrl) {
        this.proofUrl = proofUrl;
    }

}