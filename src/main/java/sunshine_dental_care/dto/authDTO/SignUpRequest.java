package sunshine_dental_care.dto.authDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        // optional (tự sinh nếu null / rỗng)
        @Size(max = 100, message = "Username must be ≤ 100")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email")
        String email,

        // phone: để optional theo yêu cầu mới (không bắt buộc)
        @Size(max = 50, message = "Phone too long")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be ≥ 6 chars")
        String password,

        String avatarUrl,

        Integer clinicId, // dùng để sinh patient code; nếu null dùng default
        String locale      // "vi" | "en" (nếu null → "en")
) {}
