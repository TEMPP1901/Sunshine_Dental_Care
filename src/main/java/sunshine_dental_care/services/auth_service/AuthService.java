package sunshine_dental_care.services.auth_service;

import sunshine_dental_care.dto.authDTO.SignUpRequest;
import sunshine_dental_care.dto.authDTO.SignUpResponse;

public interface AuthService {
    SignUpResponse signUp(SignUpRequest req);
}
