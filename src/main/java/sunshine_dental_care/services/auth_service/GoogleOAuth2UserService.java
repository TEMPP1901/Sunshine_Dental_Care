package sunshine_dental_care.services.auth_service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserRoleRepo userRoleRepo;
    private final PatientRepo patientRepo;
    private final PatientCodeService patientCodeService;
    private final MailService mailService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        var delegate = new DefaultOAuth2UserService();
        var oauthUser = delegate.loadUser(req);

        // --- Lấy thông tin từ Google ---
        String email   = safeStr(oauthUser.getAttributes().get("email"));
        String name    = safeStr(oauthUser.getAttributes().getOrDefault("name", email));
        String picture = safeStr(oauthUser.getAttributes().get("picture"));

        if (email.isBlank()) {

            throw new OAuth2AuthenticationException("Google account does not expose an email.");
        }

        // kiểm tra xem user đã dùng mail trên sign up chưa, nếu trùng không tạo user mới, cho phép login
        // nếu chưa có thì tạo mới
        User user = userRepo.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name.isBlank() ? email : name);
            if (!picture.isBlank()) u.setAvatarUrl(picture);
            u.setProvider("google");
            u.setIsActive(true);
            // tạo username từ email (đơn giản)
            u.setUsername(genUsernameFromEmail(email));
            return userRepo.save(u);
        });

        if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && !picture.isBlank()) {
            user.setAvatarUrl(picture);
        }

        ensureDefaultUserRole(user);

        ensurePatientAndSendMail(user);

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        List<String> roleNames = userRoleRepo.findRoleNamesByUserId(user.getId());
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        for (String r : roleNames) {
            authorities.add(new SimpleGrantedAuthority(r));
        }

        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        String nameAttributeKey = oauthUser.getAttributes().containsKey("sub") ? "sub" : "email";
        return new DefaultOAuth2User(authorities, oauthUser.getAttributes(), nameAttributeKey);
    }

    private void ensureDefaultUserRole(User u) {
        // lấy tất cả role names hiện có
        List<String> roles = userRoleRepo.findRoleNamesByUserId(u.getId());
        if (roles == null || roles.isEmpty()) {
            var roleUser = roleRepo.findByRoleNameIgnoreCase("USER")
                    .orElseThrow(() -> new IllegalStateException("Missing role USER in Roles table"));
            UserRole ur = new UserRole();
            ur.setUser(u);
            ur.setRole(roleUser);
            ur.setIsActive(true);
            userRoleRepo.save(ur);
        }
    }

    private void ensurePatientAndSendMail(User u) {
        // Kiểm tra đã có patient gắn user chưa
        Optional<Patient> existing = patientRepo.findByUserId(u.getId());
        if (existing.isPresent()) return;

        // Tạo mới patient
        Patient p = new Patient();
        p.setUser(u);
        p.setFullName(u.getFullName());
        p.setEmail(u.getEmail());
        p.setPhone(u.getPhone());
        p.setIsActive(true);

        String patientCode = patientCodeService.nextPatientCode();
        p.setPatientCode(patientCode);
        patientRepo.save(p);

        try {
            mailService.sendPatientCodeEmail(p, "en");
        } catch (Exception ignored) {

        }
    }

    private String genUsernameFromEmail(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_\\-\\.]", "");
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

    private String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

