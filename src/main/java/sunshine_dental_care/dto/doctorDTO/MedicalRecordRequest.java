package sunshine_dental_care.dto.doctorDTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record MedicalRecordRequest(
        @NotNull(message = "Clinic id is required")
        Integer clinicId,

        @NotNull(message = "Doctor id is required")
        Integer doctorId,

        Integer appointmentId,
        Integer serviceId,

        @NotBlank(message = "Diagnosis is required")
        String diagnosis,
        String treatmentPlan,
        String prescriptionNote,
        String note,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate recordDate
) {
}

