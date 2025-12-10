package sunshine_dental_care.dto.patientDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientProfileDTO {
    private Integer patientId;
    private String fullName;
    private String phone;
    private String email;
    private String gender;          // Male, Female, Other
    private LocalDate dateOfBirth;
    private String address;
    private String note;            // Ghi chú của bệnh nhân (VD: Tiền sử dị ứng...)
    private String patientCode;
}