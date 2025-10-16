package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(name = "PatientSequences")
public class PatientSequence {
    @Id
    @Column(name = "clinicId", nullable = false)
    private Integer id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinicId", nullable = false)
    private Clinic clinics;

    @ColumnDefault("0")
    @Column(name = "currentNumber", nullable = false)
    private Integer currentNumber;

    @Nationalized
    @ColumnDefault("N'SDC'")
    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Clinic getClinics() {
        return clinics;
    }

    public void setClinics(Clinic clinics) {
        this.clinics = clinics;
    }

    public Integer getCurrentNumber() {
        return currentNumber;
    }

    public void setCurrentNumber(Integer currentNumber) {
        this.currentNumber = currentNumber;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

}