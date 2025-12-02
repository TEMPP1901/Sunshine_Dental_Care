package sunshine_dental_care.services.auth_service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.services.jwt_security.JwtService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final UserRoleRepo userRoleRepo;
    private final RoleRepo roleRepo;

    // --- INJECT CÁC SERVICE CẦN THIẾT ---
    private final PatientRepo patientRepo;
    private final PatientCodeService patientCodeService;
    private final MailService mailService;

    @Value("${app.oauth2.redirect-uri-success}")
    private String redirectUriSuccess;

    @Override
    @Transactional // Quan trọng: Đảm bảo User, Patient, Role cùng thành công hoặc cùng rollback
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String avatar = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        // Lấy locale từ Google (mặc định "vi" nếu không có)
        Object localeObj = oAuth2User.getAttribute("locale");
        String locale = (localeObj != null) ? localeObj.toString() : "vi";

        // 1. Tìm User hoặc Tạo mới (Auto Register)
        User u = userRepo.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    // === BƯỚC 1: SINH MÃ BỆNH NHÂN ===
                    String patientCode = patientCodeService.nextPatientCode();

                    // === BƯỚC 2: TẠO USER ===
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFullName(name);
                    // Tạo username tạm từ email
                    newUser.setUsername(email.split("@")[0]);
                    newUser.setAvatarUrl(avatar);
                    newUser.setIsActive(true);
                    newUser.setProvider("google");
                    newUser.setProviderId(googleId);

                    // --- QUAN TRỌNG: ĐỂ NULL ĐỂ FRONTEND BIẾT LÀ CHƯA CÓ PASS ---
                    newUser.setPasswordHash(null);

                    // Lưu mã bệnh nhân vào User
                    newUser.setCode(patientCode);
                    newUser.setFailedLoginAttempts(0);

                    // Lưu User để lấy ID
                    User savedUser = userRepo.save(newUser);

                    // === BƯỚC 3: TẠO PATIENT VÀ LIÊN KẾT ===
                    Patient newPatient = new Patient();
                    newPatient.setUser(savedUser); // Liên kết khóa ngoại userId
                    newPatient.setFullName(savedUser.getFullName());
                    newPatient.setEmail(savedUser.getEmail());
                    // Phone từ Google thường null, user sẽ cập nhật sau ở trang MyAccount
                    newPatient.setPhone(null);
                    newPatient.setPatientCode(patientCode);
                    newPatient.setIsActive(true);

                    patientRepo.save(newPatient);

                    // === BƯỚC 4: GÁN QUYỀN "USER" ===
                    Role userRole = roleRepo.findByRoleName("USER") // ID 6 trong DB của bạn
                            .orElseThrow(() -> new RuntimeException("Error: Role 'USER' not found in DB."));

                    UserRole newRoleMap = new UserRole();
                    newRoleMap.setUser(savedUser);
                    newRoleMap.setRole(userRole);
                    newRoleMap.setIsActive(true);
                    userRoleRepo.save(newRoleMap);

                    // === BƯỚC 5: GỬI EMAIL CHÀO MỪNG + MÃ BỆNH NHÂN ===
                    try {
                        // Truyền thẳng đối tượng Patient vừa tạo để tối ưu
                        mailService.sendPatientCodeEmail(newPatient, locale);
                    } catch (Exception e) {
                        System.err.println("Gửi mail thất bại (không ảnh hưởng login): " + e.getMessage());
                    }

                    return savedUser;
                });

        // 2. Lấy Role để tạo Token
        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        if (roles == null || roles.isEmpty()) {
            roles = new ArrayList<>();
            roles.add("USER");
        }

        // 3. Tạo Token
        String token = jwtService.generateToken(u.getId(), u.getEmail(), u.getFullName(), roles);

        // 4. Redirect về Frontend
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