package sunshine_dental_care.dto.authDTO;

import jakarta.validation.constraints.NotBlank;

public record PhoneLoginStep2Request(
        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String phone,

        @NotBlank(message = "Vui lòng nhập mã OTP")
        String otp,

        // Thêm trường locale (vi/en)
        String locale
) {}