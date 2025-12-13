package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecordDTO {
    private Integer recordId;
    private ClinicDTO clinic;
    private PatientDTO patient;
    private DoctorDTO doctor;

    private Integer appointmentId;
    
    private ServiceDTO service;
    private ServiceVariantDTO serviceVariant;

    private String diagnosis;
    private String treatmentPlan;
    private String prescriptionNote;
    private String note;

    private LocalDate recordDate;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MedicalRecordImageDTO> images;
}

