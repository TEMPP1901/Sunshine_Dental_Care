package sunshine_dental_care.dto.authDTO;

import jakarta.validation.constraints.NotBlank;

public record PhonePasswordLoginRequest(
        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String phone,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        String password
) {}