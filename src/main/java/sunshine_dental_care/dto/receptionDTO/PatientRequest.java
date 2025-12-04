package sunshine_dental_care.dto.receptionDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientRequest {
    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;
    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String address;
    private String email;
}
