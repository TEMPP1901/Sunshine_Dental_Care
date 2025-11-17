package sunshine_dental_care.services.auth_service;

import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.authDTO.LoginRequest;
import sunshine_dental_care.dto.authDTO.LoginResponse;
import sunshine_dental_care.dto.authDTO.SignUpRequest;
import sunshine_dental_care.dto.authDTO.SignUpResponse;
import sunshine_dental_care.dto.authDTO.*;
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
        u.setPasswordHash(encoder.encode(req.password())); // BCrypt
        u.setAvatarUrl(req.avatarUrl());
        u.setProvider("local");
        u.setIsActive(true);

        try {
            userRepo.save(u);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ex;
        }

        ensureDefaultUserRole(u);

        // 2) Tạo Patient + sinh patientCode
        Patient p = new Patient();
        p.setUser(u);
        p.setFullName(u.getFullName());
        p.setEmail(u.getEmail());
        p.setPhone(u.getPhone());
        p.setIsActive(true);

        String patientCode = patientCodeService.nextPatientCode();
        p.setPatientCode(patientCode);

        patientRepo.save(p);

        String locale = (req.locale() == null || req.locale().isBlank()) ? "en" : req.locale();
        mailService.sendPatientCodeEmail(p, locale);

        return new SignUpResponse(u.getId(), p.getId(), patientCode, u.getAvatarUrl()
        );
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest req) {
        User u = userRepo.findByEmailIgnoreCase(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (Boolean.FALSE.equals(u.getIsActive())) {
            throw new BadCredentialsException("Account is disabled");
        }

        if (u.getPasswordHash() == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Lấy roles từ UserRoles; nếu trống → gán ROLE_USER
        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        if (roles.isEmpty()) {
            ensureDefaultUserRole(u);
            roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        }

        u.setLastLoginAt(Instant.now());
        userRepo.save(u);

        // Phát token JWT
        String token = jwtService.generateToken(u.getId(), u.getEmail(), u.getFullName(), roles);

        // Trả response cho FE
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

        // Account Google-only
        if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("This account has no password. Please use Set Password instead.");
        }

        // Sai current password
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // DTO đã check: confirm match
        u.setPasswordHash(encoder.encode(req.newPassword()));
        userRepo.save(u);
    }
}