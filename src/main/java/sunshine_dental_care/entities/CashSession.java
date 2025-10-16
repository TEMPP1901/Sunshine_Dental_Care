package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "CashSessions")
public class CashSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sessionId", nullable = false)
    private Integer id;

    @Column(name = "clinicId", nullable = false)
    private Integer clinicId;

    @Column(name = "openedBy", nullable = false)
    private Integer openedBy;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "openedAt", nullable = false)
    private Instant openedAt;

    @Column(name = "closedBy")
    private Integer closedBy;

    @Column(name = "closedAt")
    private Instant closedAt;

    @ColumnDefault("0")
    @Column(name = "openingFloat", nullable = false, precision = 18, scale = 2)
    private BigDecimal openingFloat;

    @Column(name = "closingTotal", precision = 18, scale = 2)
    private BigDecimal closingTotal;

    @Column(name = "variance", precision = 18, scale = 2)
    private BigDecimal variance;

    @Nationalized
    @Column(name = "note", length = 400)
    private String note;

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

    public Integer getOpenedBy() {
        return openedBy;
    }

    public void setOpenedBy(Integer openedBy) {
        this.openedBy = openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Integer getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(Integer closedBy) {
        this.closedBy = closedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public BigDecimal getOpeningFloat() {
        return openingFloat;
    }

    public void setOpeningFloat(BigDecimal openingFloat) {
        this.openingFloat = openingFloat;
    }

    public BigDecimal getClosingTotal() {
        return closingTotal;
    }

    public void setClosingTotal(BigDecimal closingTotal) {
        this.closingTotal = closingTotal;
    }

    public BigDecimal getVariance() {
        return variance;
    }

    public void setVariance(BigDecimal variance) {
        this.variance = variance;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

}