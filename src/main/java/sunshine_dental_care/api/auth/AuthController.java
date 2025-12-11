package sunshine_dental_care.api.auth;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import sunshine_dental_care.dto.authDTO.*;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // =================================================================
    // CÁC TÍNH NĂNG CƠ BẢN (EMAIL & GOOGLE)
    // =================================================================

    // 1. Đăng ký tài khoản
    @PostMapping("/sign-up")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest body) {
        return ResponseEntity.ok(authService.signUp(body));
    }

    // 2. Đăng nhập bằng Email + Mật khẩu
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ResponseEntity.ok(authService.login(body));
    }

    // 3. Redirect sang Google Login
    @GetMapping("/google")
    public void google(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    // 4. Đổi mật khẩu (Yêu cầu đã đăng nhập)
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        authService.changePassword(cu.userId(), req);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
    }

    // 5. Quên mật khẩu (Gửi mail)
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of("message", "Link đặt lại mật khẩu đã được gửi đến email của bạn."));
    }

    // 6. Đặt lại mật khẩu (Nhập mật khẩu mới từ link email)
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập ngay."));
    }

    // 7. Xác thực tài khoản (Link kích hoạt email)
    @PostMapping("/verify-account")
    public ResponseEntity<?> verifyAccount(@RequestParam("token") String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok(Map.of("message", "Kích hoạt tài khoản thành công!"));
    }

    // =================================================================
    // CÁC TÍNH NĂNG MỚI: ĐĂNG NHẬP BẰNG SỐ ĐIỆN THOẠI
    // =================================================================

    // 8. Đăng nhập SĐT (Bước 1: Gửi OTP)
    // [QUAN TRỌNG: Hàm này bạn bị thiếu lúc nãy]
    @PostMapping("/login-phone/step1")
    public ResponseEntity<?> loginPhoneStep1(@Valid @RequestBody PhoneLoginStep1Request body) {
        authService.sendLoginOtp(body);
        return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi đến số điện thoại của bạn."));
    }

    // 9. Đăng nhập SĐT (Bước 2: Verify OTP lấy Token)
    // [QUAN TRỌNG: Hàm này bạn bị thiếu lúc nãy]
    @PostMapping("/login-phone/step2")
    public ResponseEntity<LoginResponse> loginPhoneStep2(@Valid @RequestBody PhoneLoginStep2Request body) {
        return ResponseEntity.ok(authService.loginByPhone(body));
    }

    // 10. Đăng nhập SĐT + Mật khẩu (Không dùng OTP)
    @PostMapping("/login-phone/password")
    public ResponseEntity<LoginResponse> loginPhonePassword(@Valid @RequestBody PhonePasswordLoginRequest body) {
        return ResponseEntity.ok(authService.loginByPhoneAndPassword(body));
    }

    // 11. [MỚI] Gửi lại email kích hoạt
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest body) {
        authService.resendVerificationEmail(body.email());
        return ResponseEntity.ok(Map.of("message", "Link kích hoạt mới đã được gửi đến email của bạn."));
    }
}