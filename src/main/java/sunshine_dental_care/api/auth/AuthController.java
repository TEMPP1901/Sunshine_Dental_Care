package sunshine_dental_care.api.auth;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.authDTO.SignUpRequest;
import sunshine_dental_care.dto.authDTO.SignUpResponse;
import sunshine_dental_care.services.auth_service.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sign-up")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest body) {
        return ResponseEntity.ok(authService.signUp(body));
    }
}
