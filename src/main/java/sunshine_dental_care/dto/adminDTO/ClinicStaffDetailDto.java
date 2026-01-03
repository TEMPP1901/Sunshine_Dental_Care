    package sunshine_dental_care.dto.adminDTO;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicStaffDetailDto {
    private Integer userId;
    private String fullName;
    private String email;
    private String phone;
    private String roleName;
    private String roleAtClinic;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private Boolean isDoctor;
}

