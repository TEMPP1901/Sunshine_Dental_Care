package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(name = "InvoiceSequences")
public class InvoiceSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seqId", nullable = false)
    private Integer id;

    @Column(name = "clinicId", nullable = false)
    private Integer clinicId;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @ColumnDefault("0")
    @Column(name = "currentNumber", nullable = false)
    private Integer currentNumber;

    @Nationalized
    @Column(name = "prefix", length = 20)
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

    public Integer getClinicId() {
        return clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
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