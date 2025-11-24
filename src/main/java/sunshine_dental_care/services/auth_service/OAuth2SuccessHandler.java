package sunshine_dental_care.services.auth_service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // Nên thêm Transactional
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.PatientRepo; // <--- Cần thêm Repo này
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

    // --- INJECT CÁC SERVICE CẦN THIẾT ---
    private final PatientRepo patientRepo;           // Để lưu Patient
    private final PatientCodeService patientCodeService; // Để sinh mã SDC-...
    private final MailService mailService;           // Để gửi mail

    @Value("${app.oauth2.redirect-uri-success}")
    private String redirectUriSuccess;

    @Override
    @Transactional // Đảm bảo User và Patient cùng thành công hoặc cùng thất bại
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String avatar = oAuth2User.getAttribute("picture");
        String googleId = oAuth2User.getAttribute("sub");

        // Lấy locale để gửi mail (vi/en)
        Object localeObj = oAuth2User.getAttribute("locale");
        String locale = (localeObj != null) ? localeObj.toString() : "vi";

        // 1. Logic chính: Tìm User hoặc Tạo mới Full bộ (User + Patient)
        User u = userRepo.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    // === BƯỚC 1: CHUẨN BỊ DỮ LIỆU ===
                    // Sinh mã bệnh nhân trước (ví dụ: SDC-00001)
                    String patientCode = patientCodeService.nextPatientCode();

                    // === BƯỚC 2: TẠO USER ===
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFullName(name);
                    newUser.setUsername(email);
                    newUser.setAvatarUrl(avatar);
                    newUser.setIsActive(true);
                    newUser.setProvider("google");
                    newUser.setProviderId(googleId);
                    newUser.setPasswordHash(UUID.randomUUID().toString());

                    // Lưu mã bệnh nhân vào bảng User (field 'code')
                    newUser.setCode(patientCode);

                    // Lưu User xuống DB để lấy ID
                    User savedUser = userRepo.save(newUser);

                    // === BƯỚC 3: TẠO PATIENT VÀ LIÊN KẾT ===
                    Patient newPatient = new Patient();
                    // LIÊN KẾT QUAN TRỌNG: Set User cho Patient
                    newPatient.setUser(savedUser);

                    // Map các thông tin từ User sang Patient
                    newPatient.setFullName(savedUser.getFullName());
                    newPatient.setEmail(savedUser.getEmail());
                    newPatient.setPhone(savedUser.getPhone()); // Có thể null nếu Google ko trả về
                    newPatient.setPatientCode(patientCode); // Lưu mã vào bảng Patient
                    newPatient.setIsActive(true);

                    // Lưu Patient xuống DB
                    patientRepo.save(newPatient);

                    // === BƯỚC 4: GÁN QUYỀN (ROLE) ===
                    Role userRole = roleRepo.findByRoleName("USER") // ID 6
                            .orElseThrow(() -> new RuntimeException("Error: Role 'USER' not found."));

                    UserRole newRoleMap = new UserRole();
                    newRoleMap.setUser(savedUser);
                    newRoleMap.setRole(userRole);
                    newRoleMap.setIsActive(true);
                    userRoleRepo.save(newRoleMap);

                    // === BƯỚC 5: GỬI EMAIL ===
                    // Gửi mail kèm mã bệnh nhân
                    try {
                        mailService.sendPatientCodeEmail(savedUser, locale);
                    } catch (Exception e) {
                        System.err.println("Gửi mail thất bại: " + e.getMessage());
                        // Không throw lỗi để user vẫn login được
                    }

                    return savedUser;
                });

        // 2. Lấy Role Names để tạo Token
        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        if (roles == null || roles.isEmpty()) {
            roles = new ArrayList<>();
            roles.add("USER");
        }

        // 3. Tạo Token JWT
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