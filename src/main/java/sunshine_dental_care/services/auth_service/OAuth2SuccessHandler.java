package sunshine_dental_care.services.auth_service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.services.jwt_security.JwtService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final UserRoleRepo userRoleRepo;
    private final RoleRepo roleRepo;

    @Value("${app.oauth2.redirect-uri-success}")
    private String redirectUriSuccess;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String avatar = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        // 1. Logic: Tìm User cũ hoặc Tạo mới (Auto Register)
        User u = userRepo.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    // --- TẠO USER MỚI ---
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFullName(name);
                    newUser.setUsername(email);
                    newUser.setAvatarUrl(avatar);
                    newUser.setIsActive(true);
                    newUser.setProvider("google");
                    newUser.setProviderId(googleId);
                    newUser.setPasswordHash(UUID.randomUUID().toString());

                    User savedUser = userRepo.save(newUser);

                    // --- GÁN QUYỀN (FIX LỖI Ở ĐÂY) ---
                    // Trong DB của bạn role tên là "USER" (id=6), không phải "PATIENT"
                    Role patientRole = roleRepo.findByRoleName("USER")
                            .orElseThrow(() -> new RuntimeException("Error: Role 'USER' not found in DB. Check table Roles id 6."));

                    UserRole newRoleMap = new UserRole();
                    newRoleMap.setUser(savedUser);
                    newRoleMap.setRole(patientRole);
                    newRoleMap.setIsActive(true);

                    userRoleRepo.save(newRoleMap);

                    return savedUser;
                });

        // 2. Lấy Role Names để tạo Token
        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());

        if (roles == null || roles.isEmpty()) {
            roles = new ArrayList<>();
            // Lưu ý: Token cũng nên mang role là USER cho khớp với DB
            roles.add("USER");
        }

        // 3. Tạo Token
        String token = jwtService.generateToken(u.getId(), u.getEmail(), u.getFullName(), roles);

        // 4. Redirect
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