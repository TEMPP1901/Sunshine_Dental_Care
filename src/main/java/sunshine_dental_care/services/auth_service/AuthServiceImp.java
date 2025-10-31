package sunshine_dental_care.services.auth_service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.authDTO.SignUpRequest;
import sunshine_dental_care.dto.authDTO.SignUpResponse;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;

@Service
@RequiredArgsConstructor
public class AuthServiceImp implements AuthService {

    private final UserRepo userRepo;
    private final PatientRepo patientRepo;
    private final PasswordEncoder encoder;
    private final PatientCodeService patientCodeService;
    private final MailService mailService;

    private Integer defaultClinicId() { return 1; }

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

    @Override
    @Transactional
    public SignUpResponse signUp(SignUpRequest req) {
        userRepo.findByEmailIgnoreCase(req.email()).ifPresent(u -> {
            throw new IllegalArgumentException("Email is already registered");
        });

        if (req.username() != null && !req.username().isBlank() &&
                userRepo.findByUsernameIgnoreCase(req.username()).isPresent()) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User u = new User();
        u.setFullName(req.fullName());
        u.setUsername(resolveUsername(req.username(), req.email()));
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setAvatarUrl(req.avatarUrl());
        u.setProvider("local");
        u.setIsActive(true);

        try {
            userRepo.save(u);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            Throwable root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
            throw new IllegalArgumentException("Sign up failed: " + root.getMessage(), ex);
        }

        Patient p = new Patient();
        p.setUser(u);
        p.setFullName(u.getFullName());
        p.setEmail(u.getEmail());
        p.setPhone(u.getPhone());
        p.setIsActive(true);

        Integer clinicIdForCode = (req.clinicId() != null ? req.clinicId() : defaultClinicId());
        String patientCode = patientCodeService.nextPatientCode(clinicIdForCode);
        p.setPatientCode(patientCode);

        patientRepo.save(p);

        String locale = (req.locale() == null || req.locale().isBlank()) ? "en" : req.locale();
        mailService.sendPatientCodeEmail(p, locale);

        return new SignUpResponse(u.getId(), p.getId(), patientCode, u.getAvatarUrl());
    }
}
