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
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepo userRepo;
    private final UserRoleRepo userRoleRepo;
    // Đã xóa: PatientRepo, PatientCodeService, MailService (Không cần dùng ở đây nữa)

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        var delegate = new DefaultOAuth2UserService();
        var oauthUser = delegate.loadUser(req);

        // 1. Lấy thông tin cơ bản từ Google
        String email = safeStr(oauthUser.getAttributes().get("email"));

        if (email.isBlank()) {
            throw new OAuth2AuthenticationException("Google account does not expose an email.");
        }

        // 2. Kiểm tra User trong DB để lấy Quyền (Roles)
        // Lưu ý: Chúng ta KHÔNG TẠO MỚI user ở đây nữa.
        // Việc tạo user + gửi mail đã được chuyển sang OAuth2SuccessHandler.
        Optional<User> userOpt = userRepo.findByEmailIgnoreCase(email);

        var authorities = new ArrayList<SimpleGrantedAuthority>();

        if (userOpt.isPresent()) {
            // Nếu user đã tồn tại -> Lấy quyền từ DB lên
            User user = userOpt.get();
            List<String> roleNames = userRoleRepo.findRoleNamesByUserId(user.getId());

            for (String r : roleNames) {
                authorities.add(new SimpleGrantedAuthority(r));
            }
        }

        // Nếu user chưa tồn tại hoặc chưa có quyền -> Gán quyền mặc định tạm thời
        // (Để Spring Security cho phép đi tiếp vào SuccessHandler - nơi sẽ thực sự tạo user)
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("USER"));
        }

        String nameAttributeKey = oauthUser.getAttributes().containsKey("sub") ? "sub" : "email";

        return new DefaultOAuth2User(authorities, oauthUser.getAttributes(), nameAttributeKey);
    }

    private String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}