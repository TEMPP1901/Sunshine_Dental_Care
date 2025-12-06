package sunshine_dental_care.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "MedicalRecords")
@Getter
@Setter
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
}