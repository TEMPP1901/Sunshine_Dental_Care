package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "PrescriptionItems")
public class PrescriptionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescriptionId", nullable = false)
    private Prescription prescription;

    @Nationalized
    @Column(name = "medicationName", nullable = false, length = 200)
    private String medicationName;

    @Nationalized
    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Nationalized
    @Column(name = "dosage", length = 100)
    private String dosage;

    @Nationalized
    @Column(name = "usageInstruction", length = 300)
    private String usageInstruction;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Prescription getPrescription() {
        return prescription;
    }

    public void setPrescription(Prescription prescription) {
        this.prescription = prescription;
    }

    public String getMedicationName() {
        return medicationName;
    }

    public void setMedicationName(String medicationName) {
        this.medicationName = medicationName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getUsageInstruction() {
        return usageInstruction;
    }

    public void setUsageInstruction(String usageInstruction) {
        this.usageInstruction = usageInstruction;
    }

}