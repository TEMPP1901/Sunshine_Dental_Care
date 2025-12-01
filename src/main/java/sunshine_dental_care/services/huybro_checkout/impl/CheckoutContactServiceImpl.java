package sunshine_dental_care.services.huybro_checkout.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.huybro_checkout.CheckoutContactInfoDto;
import sunshine_dental_care.entities.User;

import sunshine_dental_care.repositories.huybro_custom.UserCustomRepository;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutContactService;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutContactServiceImpl implements CheckoutContactService {

    private final UserCustomRepository userRepository;

    @Override
    public CheckoutContactInfoDto getCurrentUserContactInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CurrentUser currentUser)) {
            log.warn("[CheckoutContactService] No authenticated user found");
            throw new IllegalStateException("User is not authenticated");
        }

        Integer userId = currentUser.userId();
        log.info("[CheckoutContactService] Start loading User info for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("[CheckoutContactService] User not found in DB, userId={}", userId);
                    return new IllegalStateException("User not found");
                });

        CheckoutContactInfoDto dto = new CheckoutContactInfoDto();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());

        log.info("[CheckoutContactService] Loaded contact info: fullName='{}', email='{}', phone='{}'",
                dto.getFullName(), dto.getEmail(), dto.getPhone());

        return dto;
    }
}
