package sunshine_dental_care.api.auth;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Import thêm cái này
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder; // Import thêm cái này
import org.springframework.web.bind.annotation.*;

import sunshine_dental_care.dto.authDTO.*;
import sunshine_dental_care.payload.request.GoogleMobileLoginRequest;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ... [CÁC API CŨ GIỮ NGUYÊN - TỪ sign-up ĐẾN resend-verification] ...

    @PostMapping("/sign-up")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest body) {
        return ResponseEntity.ok(authService.signUp(body));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ResponseEntity.ok(authService.login(body));
    }

    @GetMapping("/google")
    public void google(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication authentication) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        authService.changePassword(cu.userId(), req);
        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of("message", "Link đặt lại mật khẩu đã được gửi đến email của bạn."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập ngay."));
    }

    @PostMapping("/verify-account")
    public ResponseEntity<?> verifyAccount(@RequestParam("token") String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok(Map.of("message", "Kích hoạt tài khoản thành công!"));
    }

    @PostMapping("/login-phone/step1")
    public ResponseEntity<?> loginPhoneStep1(@Valid @RequestBody PhoneLoginStep1Request body) {
        authService.sendLoginOtp(body);
        return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi đến số điện thoại của bạn."));
    }

    @PostMapping("/login-phone/step2")
    public ResponseEntity<LoginResponse> loginPhoneStep2(@Valid @RequestBody PhoneLoginStep2Request body) {
        return ResponseEntity.ok(authService.loginByPhone(body));
    }

    @PostMapping("/login-phone/password")
    public ResponseEntity<LoginResponse> loginPhonePassword(@Valid @RequestBody PhonePasswordLoginRequest body) {
        return ResponseEntity.ok(authService.loginByPhoneAndPassword(body));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest body) {
        authService.resendVerificationEmail(body.email());
        return ResponseEntity.ok(Map.of("message", "Link kích hoạt mới đã được gửi đến email của bạn."));
    }

    @PostMapping("/google-mobile")
    public ResponseEntity<LoginResponse> googleMobileLogin(@RequestBody GoogleMobileLoginRequest body) {
        return ResponseEntity.ok(authService.loginGoogleMobile(body));
    }

    // =================================================================
    // [MỚI] API QR CODE LOGIN HANDOVER
    // =================================================================

    // 1. Web gọi: Lấy mã QR Token (Token ngắn hạn)
    // Yêu cầu: Header phải có Authorization: Bearer <token_đang_đăng_nhập>
    @GetMapping("/qr-generate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> generateQrCode() {
        // Lấy email user đang đăng nhập hiện tại
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String qrToken = authService.generateQrToken(email);

        // Trả về JSON: { "result": "eyKhongL..." }
        return ResponseEntity.ok(Map.of("result", qrToken));
    }

    // 2. Mobile gọi: Quét mã để lấy Token chính thức
    // Public API (không cần Header Auth cũ, vì đang đi xin token mới)
    @PostMapping("/qr-login")
    public ResponseEntity<Map<String, Object>> loginWithQr(@RequestParam("token") String token) {
        LoginResponse response = authService.loginWithQrCode(token);
        // Trả về response giống hệt login bình thường
        return ResponseEntity.ok(Map.of(
                "message", "QR Login Success",
                "result", response
        ));
    }
}