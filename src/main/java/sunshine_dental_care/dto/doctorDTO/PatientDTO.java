package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDTO {
    private Integer id;
    private String patientCode;
    private String fullName;
    private String phone;
    private String email;
}

