package sunshine_dental_care.dto.huybro_payroll;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSearchResponse {
    private Integer id;        // Khớp với frontend
    private String fullName;
    private String email;
    private String code;       // Mã nhân viên
    private String roleName;   // Thêm role để biết chức vụ gì (nếu cần)
}