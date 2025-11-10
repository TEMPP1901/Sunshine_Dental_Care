package sunshine_dental_care.dto.authDTO;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 100, message = "Password length must be 6-100")
        @Pattern(
                regexp = "^(?=\\S+$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,100}$",
                message = "Password must include upper, lower, digit, and special char; no spaces"
        )
        String newPassword,

        @NotBlank(message = "Confirm password is required")
        String confirmNewPassword
) {
    @AssertTrue(message = "Confirm password must match")
    public boolean isConfirmMatch() {
        return newPassword != null && newPassword.equals(confirmNewPassword);
    }

    @AssertTrue(message = "New password must be different from current")
    public boolean isDifferentFromCurrent() {
        return currentPassword != null && !currentPassword.equals(newPassword);
    }
}
