package sunshine_dental_care.services.auth_service;

import sunshine_dental_care.dto.authDTO.*;
import sunshine_dental_care.payload.request.GoogleMobileLoginRequest;

public interface AuthService {
    // 1. Các tính năng cơ bản
    SignUpResponse signUp(SignUpRequest req);
    LoginResponse login(LoginRequest req);
    void changePassword(Integer currentUserId, ChangePasswordRequest req);
    void forgotPassword(ForgotPasswordRequest req);
    void resetPassword(ResetPasswordRequest req);
    void verifyAccount(String token);

    // 2. Login SĐT
    void sendLoginOtp(PhoneLoginStep1Request req);
    LoginResponse loginByPhone(PhoneLoginStep2Request req);
    LoginResponse loginByPhoneAndPassword(PhonePasswordLoginRequest req);
    void resendVerificationEmail(String email);

    // 3. Login Google Mobile
    LoginResponse loginGoogleMobile(GoogleMobileLoginRequest request);

    // =========================================================
    // 4. [MỚI] TÍNH NĂNG QR CODE LOGIN (WEB -> MOBILE)
    // =========================================================

    // Tạo mã QR (Token ngắn hạn) cho Web
    String generateQrToken(String email);

    // Mobile quét mã để đăng nhập
    LoginResponse loginWithQrCode(String qrToken);
}