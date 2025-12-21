package sunshine_dental_care.dto.patientDTO;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientAppointmentResponse {
    private Integer appointmentId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String status;
    private String note;

    private String clinicName;
    private String clinicAddress;

    private String doctorName;
    private String doctorAvatar;

    private String serviceName;
    private String variantName;

    // --- QUAN TRỌNG: Phải có trường này ---
    private Boolean canCancel;
}