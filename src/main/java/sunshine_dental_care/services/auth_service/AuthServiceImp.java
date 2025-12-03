package sunshine_dental_care.services.auth_service;

import java.time.Instant;
import java.util.List;
import java.util.UUID; // <--- Import UUID

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.authDTO.*; // Import hết các DTO (bao gồm Forgot/Reset)
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
        u.setPasswordHash(encoder.encode(req.password()));
        u.setAvatarUrl(req.avatarUrl());
        u.setProvider("local");
        u.setIsActive(true);
        u.setFailedLoginAttempts(0);

        u = userRepo.save(u);

        ensureDefaultUserRole(u);

        // 2) Sinh Patient Code
        String patientCode = patientCodeService.nextPatientCode();
        u.setCode(patientCode);
        userRepo.save(u);

        // 3) Tạo Patient
        Patient p = new Patient();
        p.setUser(u);
        p.setFullName(u.getFullName());
        p.setEmail(u.getEmail());
        p.setPhone(u.getPhone());
        p.setPatientCode(patientCode);
        p.setIsActive(true);

        patientRepo.save(p);

        // 4) Gửi mail Welcome
        String locale = (req.locale() == null || req.locale().isBlank()) ? "en" : req.locale();
        try {
            mailService.sendPatientCodeEmail(p, locale);
        } catch (Exception e) {
            System.err.println("Failed to send welcome email: " + e.getMessage());
        }

        return new SignUpResponse(u.getId(), p.getId(), patientCode, u.getAvatarUrl());
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(LoginRequest req) {
        User u = userRepo.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (Boolean.FALSE.equals(u.getIsActive())) {
            throw new BadCredentialsException("Account is locked or disabled. Please contact admin.");
        }

        if (u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            // Xử lý sai mật khẩu
            int currentFails = u.getFailedLoginAttempts() == null ? 0 : u.getFailedLoginAttempts();
            int newFails = currentFails + 1;
            u.setFailedLoginAttempts(newFails);

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

        // Đăng nhập thành công -> Reset
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
                u.getPhone(),
                roles
        );
    }

    @Override
    @Transactional
    public void changePassword(Integer currentUserId, ChangePasswordRequest req) {
        User u = userRepo.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 1. Kiểm tra thực tế trong DB xem user đã có mật khẩu chưa
        boolean hasPasswordInDb = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();

        // 2. Logic kiểm tra
        if (hasPasswordInDb) {
            // TRƯỜNG HỢP A: Đã có mật khẩu (Local hoặc Google đã từng set pass)
            // => Bắt buộc phải nhập mật khẩu cũ và phải đúng
            if (req.currentPassword() == null || req.currentPassword().isBlank()) {
                throw new IllegalArgumentException("Current password is required.");
            }

            if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
        }
        // TRƯỜNG HỢP B: Chưa có mật khẩu (Google lần đầu)
        // => Bỏ qua bước kiểm tra ở trên, cho phép set thẳng mật khẩu mới.

        // 3. Lưu mật khẩu mới
        u.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(u);
    }

    // =================================================================
    // CHỨC NĂNG QUÊN MẬT KHẨU (FORGOT PASSWORD)
    // =================================================================

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        // 1. Tìm user
        User u = userRepo.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại trong hệ thống"));

        // 2. Tạo Token ngẫu nhiên
        String token = UUID.randomUUID().toString();
        u.setResetPasswordToken(token);
        // Token hết hạn sau 15 phút
        u.setResetPasswordTokenExpiry(Instant.now().plusSeconds(900));

        userRepo.save(u);

        // 3. Gửi mail chứa link
        try {
            mailService.sendResetPasswordEmail(u, token);
        } catch (Exception e) {
            System.err.println("Lỗi gửi mail reset pass: " + e.getMessage());
            // Có thể throw exception nếu muốn Frontend biết là gửi mail lỗi
        }
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        // 1. Kiểm tra mật khẩu nhập lại
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        // 2. Tìm user bằng token
        User u = userRepo.findByResetPasswordToken(req.token())
                .orElseThrow(() -> new IllegalArgumentException("Token không hợp lệ hoặc không tồn tại."));

        // 3. Kiểm tra thời gian hết hạn
        if (u.getResetPasswordTokenExpiry() == null || u.getResetPasswordTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Link đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại.");
        }

        // 4. Cập nhật mật khẩu mới
        u.setPasswordHash(encoder.encode(req.newPassword()));

        // 5. Xóa token
        u.setResetPasswordToken(null);
        u.setResetPasswordTokenExpiry(null);

        // 6. Mở khóa tài khoản (nếu đang bị khóa)
        // Đây là bước quan trọng để user có thể đăng nhập lại ngay sau khi reset
        u.setFailedLoginAttempts(0);
        u.setIsActive(true);

        userRepo.save(u);
    }
}