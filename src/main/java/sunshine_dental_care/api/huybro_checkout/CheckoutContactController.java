package sunshine_dental_care.api.huybro_checkout;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sunshine_dental_care.dto.huybro_checkout.CheckoutContactInfoDto;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutContactService;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutContactController {

    private final CheckoutContactService checkoutContactService;

    @GetMapping("/contact-info")
    public ResponseEntity<?> getContactInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(
                    "{\"message\":\"Unauthorized\"}"
            );
        }

        CheckoutContactInfoDto dto = checkoutContactService.getCurrentUserContactInfo();
        return ResponseEntity.ok(dto);
    }
}
