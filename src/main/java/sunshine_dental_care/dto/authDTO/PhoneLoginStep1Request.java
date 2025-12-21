package sunshine_dental_care.dto.authDTO;

import jakarta.validation.constraints.NotBlank;

public record PhoneLoginStep1Request(
        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String phone,

        // Thêm trường locale (vi/en)
        String locale
) {}