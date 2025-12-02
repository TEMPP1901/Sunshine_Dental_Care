package sunshine_dental_care.services.auth_service;

import sunshine_dental_care.dto.authDTO.*;

public interface AuthService {
    // 1. Các tính năng cơ bản
    SignUpResponse signUp(SignUpRequest req);
    LoginResponse login(LoginRequest req);
    void changePassword(Integer currentUserId, ChangePasswordRequest req);
    void forgotPassword(ForgotPasswordRequest req);
    void resetPassword(ResetPasswordRequest req);
    void verifyAccount(String token);

    // 2. [QUAN TRỌNG] Ba hàm mới cho Login SĐT (Phải khai báo ở đây thì Controller mới thấy)

    // Bước 1: Gửi OTP
    void sendLoginOtp(PhoneLoginStep1Request req);

    // Bước 2: Đăng nhập bằng OTP
    LoginResponse loginByPhone(PhoneLoginStep2Request req);

    // Cách 3: Đăng nhập bằng SĐT + Mật khẩu
    LoginResponse loginByPhoneAndPassword(PhonePasswordLoginRequest req);

    void resendVerificationEmail(String email);
}