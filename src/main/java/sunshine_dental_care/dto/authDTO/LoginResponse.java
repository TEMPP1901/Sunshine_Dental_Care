package sunshine_dental_care.dto.authDTO;

import java.util.List;

public record LoginResponse (
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    Integer userId,
    String fullName,
    String email,
    String avatarUrl,
    List<String> roles
){}
