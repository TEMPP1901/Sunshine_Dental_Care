package sunshine_dental_care.dto.authDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest (
        @Email(message = "Email không hợp lệ")
        @NotBlank(message = "Vui lòng nhập email")
        String email,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        String password,

        // Thêm trường locale (vi/en)
        String locale
) {}