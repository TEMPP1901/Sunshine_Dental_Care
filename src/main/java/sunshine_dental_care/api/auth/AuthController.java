package sunshine_dental_care.api.auth;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import sunshine_dental_care.dto.authDTO.*; // Import tất cả DTO (Login, SignUp, Forgot, Reset...)
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 1. Đăng ký
    @PostMapping("/sign-up")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest body) {
        return ResponseEntity.ok(authService.signUp(body));
    }

    // 2. Đăng nhập
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        return ResponseEntity.ok(authService.login(body));
    }

    // 3. Redirect Google Login
    @GetMapping("/google")
    public void google(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    // 4. Đổi mật khẩu (Khi đã đăng nhập)
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication authentication
    ) {
        // Lấy ID user đang đăng nhập từ Security Context
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        Integer userId = cu.userId();

        authService.changePassword(userId, req);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // =================================================================
    // CÁC API MỚI CHO TÍNH NĂNG QUÊN MẬT KHẨU
    // =================================================================

    // 5. Quên mật khẩu (Gửi email chứa link)
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of("message", "Link đặt lại mật khẩu đã được gửi đến email của bạn."));
    }

    // 6. Đặt lại mật khẩu (Xử lý token từ email)
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập ngay."));
    }
}