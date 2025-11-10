package sunshine_dental_care.services.auth_service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.services.jwt_security.JwtService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final UserRoleRepo userRoleRepo;

    @Value("${app.oauth2.redirect-uri-success}")
    private String redirectUriSuccess;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = String.valueOf(oAuth2User.getAttributes().get("email"));

        User u = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("User not found after OAuth2 login"));

        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        String token = jwtService.generateToken(u.getId(), u.getEmail(), u.getFullName(), roles);

        // Gửi token; FE sẽ gọi /api/users/me để lấy user + avatar absolute
        String redirectUrl = redirectUriSuccess
                + "?access_token=" + enc(token)
                + "&token_type=Bearer"
                + "&expires_in=" + jwtService.getExpirationSeconds();

        response.sendRedirect(redirectUrl);
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
