package sunshine_dental_care.api.auth;


import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.authDTO.*;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.AuthService;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication authentication
    ) {
        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        Integer userId = cu.userId();

        authService.changePassword(userId, req);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
