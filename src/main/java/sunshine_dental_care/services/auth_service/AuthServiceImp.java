package sunshine_dental_care.services.auth_service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.authDTO.ChangePasswordRequest;
import sunshine_dental_care.dto.authDTO.LoginRequest;
import sunshine_dental_care.dto.authDTO.LoginResponse;
import sunshine_dental_care.dto.authDTO.SignUpRequest;
import sunshine_dental_care.dto.authDTO.SignUpResponse;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.auth.DuplicateEmailException;
import sunshine_dental_care.exceptions.auth.DuplicateUsernameException;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.services.jwt_security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthServiceImp implements AuthService {

    private final UserRepo userRepo;
    private final PatientRepo patientRepo;
    private final PasswordEncoder encoder;
    private final PatientCodeService patientCodeService;
    private final MailService mailService;
    private final RoleRepo roleRepo;
    private final UserRoleRepo userRoleRepo;
    private final JwtService jwtService;

    private String resolveUsername(String username, String email) {
        if (username != null && !username.isBlank()) return username.trim();

        String base = (email != null ? email.split("@")[0] : "user")
                .replaceAll("[^a-zA-Z0-9_\\-.]", "");
        if (base.isBlank()) base = "user";

        String candidate = base;
        int i = 1;
        while (userRepo.findByUsernameIgnoreCase(candidate).isPresent()) {
            candidate = base + i;
            i++;
            if (i > 9999) break;
        }
        return candidate;
    }

    private void ensureDefaultUserRole(User u) {
        // Role mặc định là USER (ID 6)
        var roleUser = roleRepo.findByRoleNameIgnoreCase("USER")
                .orElseThrow(() -> new IllegalStateException("Missing role USER in Roles table"));
        UserRole ur = new UserRole();
        ur.setUser(u);
        ur.setRole(roleUser);
        ur.setIsActive(true);
        userRoleRepo.save(ur);
    }

    @Override
    @Transactional
    public SignUpResponse signUp(SignUpRequest req) {
        userRepo.findByEmailIgnoreCase(req.email()).ifPresent(u -> {
            throw new DuplicateEmailException(req.email());
        });

        if (req.username() != null && !req.username().isBlank() &&
                userRepo.findByUsernameIgnoreCase(req.username()).isPresent()) {
            throw new DuplicateUsernameException(req.username());
        }

        // 1) Tạo User
        User u = new User();
        u.setFullName(req.fullName());
        u.setUsername(resolveUsername(req.username(), req.email()));
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password())); // BCrypt
        u.setAvatarUrl(req.avatarUrl());
        u.setProvider("local");
        u.setIsActive(true);
        u.setFailedLoginAttempts(0); // Khởi tạo số lần sai = 0

        // Lưu User lần 1 để lấy ID
        u = userRepo.save(u);

        ensureDefaultUserRole(u);

        // 2) Sinh Patient Code và cập nhật User
        String patientCode = patientCodeService.nextPatientCode();
        u.setCode(patientCode);
        userRepo.save(u);

        // 3) Tạo Patient và liên kết User
        Patient p = new Patient();
        p.setUser(u); // Liên kết khóa ngoại
        p.setFullName(u.getFullName());
        p.setEmail(u.getEmail());
        p.setPhone(u.getPhone());
        p.setPatientCode(patientCode);
        p.setIsActive(true);

        patientRepo.save(p);

        // 4) Gửi mail Welcome (Dùng object Patient)
        String locale = (req.locale() == null || req.locale().isBlank()) ? "en" : req.locale();
        try {
            mailService.sendPatientCodeEmail(p, locale);
        } catch (Exception e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }

        return new SignUpResponse(u.getId(), p.getId(), patientCode, u.getAvatarUrl());
    }

    @Override
    // KHẮC PHỤC LỖI: Không rollback khi sai password để lưu được số lần sai
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(LoginRequest req) {
        // 1. Tìm User
        User u = userRepo.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // 2. Kiểm tra khóa
        if (Boolean.FALSE.equals(u.getIsActive())) {
            throw new BadCredentialsException("Account is locked or disabled. Please contact admin.");
        }

        // 3. Kiểm tra mật khẩu
        if (u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {

            // --- TĂNG BIẾN ĐẾM ---
            int currentFails = u.getFailedLoginAttempts() == null ? 0 : u.getFailedLoginAttempts();
            int newFails = currentFails + 1;
            u.setFailedLoginAttempts(newFails);

            // Lưu ngay lập tức (Quan trọng: Nhờ noRollbackFor nên lệnh này sẽ được Commit)
            userRepo.save(u);

            if (newFails >= 5) {
                u.setIsActive(false);
                userRepo.save(u);

                try {
                    mailService.sendAccountLockedEmail(u);
                } catch (Exception e) {
                    System.err.println("Failed to send lock alert email: " + e.getMessage());
                }
                throw new BadCredentialsException("Account has been locked due to 5 failed login attempts.");
            } else {
                int remaining = 5 - newFails;
                throw new BadCredentialsException("Invalid email or password. You have " + remaining + " attempt(s) left.");
            }
        }

        // 4. Đăng nhập thành công -> Reset
        if (u.getFailedLoginAttempts() != null && u.getFailedLoginAttempts() > 0) {
            u.setFailedLoginAttempts(0);
        }

        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        if (roles.isEmpty()) {
            ensureDefaultUserRole(u);
            roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        }

        u.setLastLoginAt(Instant.now());
        userRepo.save(u);

        String token = jwtService.generateToken(u.getId(), u.getEmail(), u.getFullName(), roles);

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getAvatarUrl(),
                roles
        );
    }

    @Override
    @Transactional
    public void changePassword(Integer currentUserId, ChangePasswordRequest req) {
        User u = userRepo.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("This account has no password. Please use Set Password instead.");
        }

        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        u.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(u);
    }
}