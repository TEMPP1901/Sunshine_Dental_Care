package sunshine_dental_care.dto.receptionDTO;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PatientResponse {
    private Integer id;
    private String patientCode;
    private String fullName;
    private String gender;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String address;
    private Boolean isActive;
}