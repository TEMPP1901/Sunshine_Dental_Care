package sunshine_dental_care.dto.authDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        @Pattern(regexp = "^(?=\\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Password must be 8-100 chars, include upper, lower, digit, special char.")
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}