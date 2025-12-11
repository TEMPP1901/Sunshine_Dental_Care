    package sunshine_dental_care.dto.adminDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicUpdateRequestDto {
    private String clinicCode;
    private String clinicName;
    private String address;
    private String phone;
    private String email;
    private String openingHours;
}

