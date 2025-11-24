package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "MedicalRecords")
public class MedicalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recordId", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinicId", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patientId", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctorId", nullable = false)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointmentId")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serviceId")
    private Service service;

    @Nationalized
    @Lob
    @Column(name = "diagnosis")
    private String diagnosis;

    @Nationalized
    @Lob
    @Column(name = "treatmentPlan")
    private String treatmentPlan;

    @Nationalized
    @Lob
    @Column(name = "prescriptionNote")
    private String prescriptionNote;

    @Nationalized
    @Lob
    @Column(name = "note")
    private String note;

    @ColumnDefault("CONVERT([date], sysutcdatetime())")
    @Column(name = "recordDate", nullable = false)
    private LocalDate recordDate;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MedicalRecordImage> medicalRecordImages = new LinkedHashSet<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public void setClinic(Clinic clinic) {
        this.clinic = clinic;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public User getDoctor() {
        return doctor;
    }

    public void setDoctor(User doctor) {
        this.doctor = doctor;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getTreatmentPlan() {
        return treatmentPlan;
    }

    public void setTreatmentPlan(String treatmentPlan) {
        this.treatmentPlan = treatmentPlan;
    }

    public String getPrescriptionNote() {
        return prescriptionNote;
    }

    public void setPrescriptionNote(String prescriptionNote) {
        this.prescriptionNote = prescriptionNote;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<MedicalRecordImage> getMedicalRecordImages() {
        return medicalRecordImages;
    }

    public void setMedicalRecordImages(Set<MedicalRecordImage> medicalRecordImages) {
        this.medicalRecordImages = medicalRecordImages;
    }

}