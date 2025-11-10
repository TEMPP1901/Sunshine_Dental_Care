package sunshine_dental_care.services.auth_service;

import sunshine_dental_care.dto.authDTO.*;

public interface AuthService {
    SignUpResponse signUp(SignUpRequest req);
    LoginResponse login(LoginRequest req);
    void changePassword(Integer currentUserId, ChangePasswordRequest req);
}
