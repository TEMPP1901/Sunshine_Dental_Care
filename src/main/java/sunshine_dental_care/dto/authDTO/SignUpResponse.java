package sunshine_dental_care.dto.authDTO;

public record SignUpResponse(
        Integer userId,
        Integer patientId,
        String patientCode,
        String avatarUrl
) {}
