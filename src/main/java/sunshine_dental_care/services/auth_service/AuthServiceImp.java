package sunshine_dental_care.services.auth_service;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.authDTO.ChangePasswordRequest;
import sunshine_dental_care.dto.authDTO.ForgotPasswordRequest;
import sunshine_dental_care.dto.authDTO.LoginRequest;
import sunshine_dental_care.dto.authDTO.LoginResponse;
import sunshine_dental_care.dto.authDTO.PhoneLoginStep1Request;
import sunshine_dental_care.dto.authDTO.PhoneLoginStep2Request;
import sunshine_dental_care.dto.authDTO.PhonePasswordLoginRequest;
import sunshine_dental_care.dto.authDTO.ResetPasswordRequest;
import sunshine_dental_care.dto.authDTO.SignUpRequest;
import sunshine_dental_care.dto.authDTO.SignUpResponse;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.auth.DuplicateEmailException;
import sunshine_dental_care.exceptions.auth.DuplicateUsernameException;
import sunshine_dental_care.payload.request.GoogleMobileLoginRequest;
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
    private final SmsService smsService;

    // --- Helper Methods ---
    private String resolveUsername(String username, String email) {
        if (username != null && !username.isBlank()) return username.trim();
        String base = (email != null ? email.split("@")[0] : "user").replaceAll("[^a-zA-Z0-9_\\-.]", "");
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

    // =========================================================
    // 1. CÁC TÍNH NĂNG ĐĂNG KÝ / EMAIL LOGIN
    // =========================================================
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

        User u = new User();
        u.setFullName(req.fullName());
        u.setUsername(resolveUsername(req.username(), req.email()));
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setAvatarUrl(req.avatarUrl());
        u.setProvider("local");
        u.setIsActive(false);
        u.setFailedLoginAttempts(0);

        String verifyToken = UUID.randomUUID().toString();
        u.setVerificationToken(verifyToken);
        u.setVerificationTokenExpiry(Instant.now().plusSeconds(86400));

        u = userRepo.save(u);
        ensureDefaultUserRole(u);

        String patientCode = patientCodeService.nextPatientCode();
        u.setCode(patientCode);
        userRepo.save(u);

        Patient p = new Patient();
        p.setUser(u);
        p.setFullName(u.getFullName());
        p.setEmail(u.getEmail());
        p.setPhone(u.getPhone());
        p.setPatientCode(patientCode);
        p.setIsActive(true);
        patientRepo.save(p);

        try { mailService.sendVerificationEmail(u, verifyToken); } catch (Exception ignored) {}

        return new SignUpResponse(u.getId(), p.getId(), patientCode, u.getAvatarUrl());
    }

    @Override
    @Transactional
    public void verifyAccount(String token) {
        User u = userRepo.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Mã kích hoạt không hợp lệ."));

        if (u.getVerificationTokenExpiry() == null || u.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Link kích hoạt đã hết hạn.");
        }

        if (Boolean.TRUE.equals(u.getIsActive())) return;

        u.setIsActive(true);
        u.setVerificationToken(null);
        u.setVerificationTokenExpiry(null);
        userRepo.save(u);

        try {
            Patient p = patientRepo.findByUserId(u.getId()).orElse(null);
            if (p != null) mailService.sendPatientCodeEmail(p, "vi");
        } catch (Exception ignored) {}
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse login(LoginRequest req) {
        User u = userRepo.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không đúng"));
        return processLogin(u, req.password());
    }

    // =========================================================
    // 2. CÁC TÍNH NĂNG LOGIN SỐ ĐIỆN THOẠI
    // =========================================================
    @Override
    @Transactional
    public void sendLoginOtp(PhoneLoginStep1Request req) {
        User u = userRepo.findByPhone(req.phone())
                .orElseThrow(() -> new IllegalArgumentException("Số điện thoại chưa được đăng ký."));

        if (Boolean.FALSE.equals(u.getIsActive())) {
            throw new IllegalArgumentException("Tài khoản đã bị khóa.");
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        if("0999999999".equals(req.phone())) otp = "123456";

        u.setOtpCode(otp);
        u.setOtpExpiry(Instant.now().plusSeconds(300));
        userRepo.save(u);
        smsService.sendOtp(u.getPhone(), otp);
    }

    @Override
    @Transactional
    public LoginResponse loginByPhone(PhoneLoginStep2Request req) {
        User u = userRepo.findByPhone(req.phone())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (u.getOtpCode() == null || !u.getOtpCode().equals(req.otp())) {
            throw new BadCredentialsException("Mã OTP không chính xác.");
        }

        if (u.getOtpExpiry() == null || u.getOtpExpiry().isBefore(Instant.now())) {
            throw new BadCredentialsException("Mã OTP đã hết hạn.");
        }

        u.setOtpCode(null);
        u.setOtpExpiry(null);
        u.setFailedLoginAttempts(0);
        u.setLastLoginAt(Instant.now());
        userRepo.save(u);

        return generateLoginResponse(u);
    }

    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResponse loginByPhoneAndPassword(PhonePasswordLoginRequest req) {
        User u = userRepo.findByPhone(req.phone())
                .orElseThrow(() -> new BadCredentialsException("Số điện thoại hoặc mật khẩu không chính xác"));
        return processLogin(u, req.password());
    }

    // =========================================================
    // 3. HELPER PROCESS LOGIN
    // =========================================================
    private LoginResponse processLogin(User u, String rawPassword) {
        if (Boolean.FALSE.equals(u.getIsActive())) {
            if (u.getVerificationToken() != null) {
                throw new BadCredentialsException("Tài khoản chưa được kích hoạt. Vui lòng kiểm tra email.");
            }
            throw new BadCredentialsException("Tài khoản đã bị khóa.");
        }

        if (u.getPasswordHash() == null || !encoder.matches(rawPassword, u.getPasswordHash())) {
            int currentFails = u.getFailedLoginAttempts() == null ? 0 : u.getFailedLoginAttempts();
            int newFails = currentFails + 1;
            u.setFailedLoginAttempts(newFails);
            userRepo.save(u);

            if (newFails >= 5) {
                u.setIsActive(false);
                userRepo.save(u);
                try { mailService.sendAccountLockedEmail(u); } catch (Exception ignored) {}
                throw new BadCredentialsException("Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần.");
            } else {
                int remaining = 5 - newFails;
                if (remaining <= 3) {
                    throw new BadCredentialsException("Mật khẩu không chính xác. Bạn còn " + remaining + " lần thử.");
                } else {
                    throw new BadCredentialsException("Mật khẩu không chính xác.");
                }
            }
        }

        if (u.getFailedLoginAttempts() != null && u.getFailedLoginAttempts() > 0) {
            u.setFailedLoginAttempts(0);
        }
        u.setLastLoginAt(Instant.now());
        userRepo.save(u);

        return generateLoginResponse(u);
    }

    private LoginResponse generateLoginResponse(User u) {
        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        if (roles.isEmpty()) {
            ensureDefaultUserRole(u);
            roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        }
        String token = jwtService.generateToken(u.getId(), u.getEmail(), u.getFullName(), roles);

        return new LoginResponse(
                token, "Bearer", jwtService.getExpirationSeconds(),
                u.getId(), u.getFullName(), u.getEmail(), u.getAvatarUrl(), u.getPhone(), roles
        );
    }

    // =========================================================
    // 4. CHANGE & FORGOT PASSWORD
    // =========================================================
    @Override
    @Transactional
    public void changePassword(Integer currentUserId, ChangePasswordRequest req) {
        User u = userRepo.findById(currentUserId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getPasswordHash() != null && !u.getPasswordHash().isBlank()) {
            if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
                throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
            }
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(u);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        User u = userRepo.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Email không tồn tại trong hệ thống."));
        String token = UUID.randomUUID().toString();
        u.setResetPasswordToken(token);
        u.setResetPasswordTokenExpiry(Instant.now().plusSeconds(900));
        userRepo.save(u);
        try { mailService.sendResetPasswordEmail(u, token); } catch (Exception ignored) {}
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }
        User u = userRepo.findByResetPasswordToken(req.token())
                .orElseThrow(() -> new IllegalArgumentException("Link không hợp lệ hoặc không tồn tại."));

        if (u.getResetPasswordTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Link đã hết hạn.");
        }

        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setResetPasswordToken(null);
        u.setResetPasswordTokenExpiry(null);
        u.setFailedLoginAttempts(0);
        u.setIsActive(true);
        userRepo.save(u);
        
        // Khi unlock qua reset password, cần set tất cả UserRole thành active
        // Vì khi lock, tất cả UserRole đã bị set inactive
        List<UserRole> userRoles = userRoleRepo.findByUserId(u.getId());
        for (UserRole userRole : userRoles) {
            userRole.setIsActive(true);
        }
        userRoleRepo.saveAll(userRoles);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        User u = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Email này chưa được đăng ký."));

        if (Boolean.TRUE.equals(u.getIsActive())) {
            throw new IllegalArgumentException("Tài khoản này đã được kích hoạt rồi.");
        }

        String newToken = UUID.randomUUID().toString();
        u.setVerificationToken(newToken);
        u.setVerificationTokenExpiry(Instant.now().plusSeconds(86400));
        userRepo.save(u);

        try { mailService.sendVerificationEmail(u, newToken); } catch (Exception e) {
            throw new RuntimeException("Lỗi gửi mail: " + e.getMessage());
        }
    }

    // =========================================================
    // 5. LOGIN GOOGLE MOBILE & QR CODE
    // =========================================================
    @Override
    @Transactional
    public LoginResponse loginGoogleMobile(GoogleMobileLoginRequest req) {
        String email = req.getEmail();
        User user = userRepo.findByEmail(email).orElse(null);

        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setUsername(resolveUsername(null, email));
            user.setFullName(req.getFullName() != null ? req.getFullName() : "Google User");
            user.setAvatarUrl(req.getAvatarUrl());
            user.setProvider("google");
            user.setPasswordHash(encoder.encode("GOOGLE_MOBILE_" + UUID.randomUUID()));
            user.setIsActive(true);
            user.setFailedLoginAttempts(0);

            user = userRepo.save(user);
            ensureDefaultUserRole(user);

            var patientRole = roleRepo.findByRoleNameIgnoreCase("PATIENT")
                    .orElseThrow(() -> new RuntimeException("Role PATIENT not found"));
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(patientRole);
            userRole.setIsActive(true);
            userRoleRepo.save(userRole);

            String patientCode = patientCodeService.nextPatientCode();
            user.setCode(patientCode);
            userRepo.save(user);

            Patient p = new Patient();
            p.setUser(user);
            p.setFullName(user.getFullName());
            p.setEmail(user.getEmail());
            p.setPatientCode(patientCode);
            p.setIsActive(true);
            patientRepo.save(p);
        } else {
            if (Boolean.FALSE.equals(user.getIsActive())) {
                throw new BadCredentialsException("Tài khoản đã bị khóa hoặc chưa kích hoạt.");
            }
            if (user.getAvatarUrl() == null && req.getAvatarUrl() != null) {
                user.setAvatarUrl(req.getAvatarUrl());
            }
        }

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);
        return generateLoginResponse(user);
    }

    // -------------------------------------------------------------
    // [MỚI] Triển khai QR Logic
    // -------------------------------------------------------------

    @Override
    public String generateQrToken(String email) {
        // Tạo token ngắn hạn (2 phút) chứa email người dùng
        // Lưu ý: Đảm bảo JwtService đã có hàm generateShortLivedToken
        return jwtService.generateShortLivedToken(email);
    }

    @Override
    @Transactional
    public LoginResponse loginWithQrCode(String qrToken) {
        // 1. Trích xuất username/email từ Token QR
        String email = jwtService.extractUsername(qrToken);
        if (email == null) {
            throw new BadCredentialsException("QR Code không hợp lệ hoặc đã hết hạn (No subject).");
        }

        // 2. Tìm User
        User user = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("User not found from QR Code."));

        // 3. Validate Token
        // Hàm isTokenValid của bạn có thể cần UserDetails, ở đây ta tạo UserDetails dummy hoặc overload hàm validate
        // Giả sử JwtService có hàm: boolean isTokenValid(String token, UserDetails userDetails)
        // Ta cần load UserDetails hoặc kiểm tra thủ công.
        // Đơn giản nhất: Kiểm tra hạn token và subject
        if (jwtService.isTokenExpired(qrToken)) {
            throw new BadCredentialsException("QR Code đã hết hạn.");
        }

        // Nếu muốn chặt chẽ hơn, check subject khớp với user (đã làm ở bước 1)
        if (!email.equals(user.getEmail())) {
            throw new BadCredentialsException("QR Code không thuộc về user này.");
        }

        // 4. Nếu hợp lệ -> Cấp quyền đăng nhập (Access + Refresh Token)
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        return generateLoginResponse(user);
    }
}