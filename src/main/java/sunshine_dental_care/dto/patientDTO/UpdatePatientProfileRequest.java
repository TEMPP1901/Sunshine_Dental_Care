package sunshine_dental_care.dto.patientDTO;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdatePatientProfileRequest {
    private String fullName;
    private String gender;
    private LocalDate dateOfBirth;
    private String address;
    private String phone;
    private String note;
}