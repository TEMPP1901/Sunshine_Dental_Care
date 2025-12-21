package sunshine_dental_care.api.huybro_checkout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.services.huybro_checkout.vnpay.dto.VnpayPaymentUrlDto;
import sunshine_dental_care.services.huybro_checkout.vnpay.services.VnpayCheckoutService;

import java.util.Map;

@RestController
@RequestMapping("/api/checkout/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VnpayCheckoutController {

    private final VnpayCheckoutService vnpayService;

    // Bước 1: Tạo URL thanh toán
    @PostMapping("/create-payment-url")
    public ResponseEntity<VnpayPaymentUrlDto> createPaymentUrl(
            HttpSession session,
            HttpServletRequest request
    ) {
        VnpayPaymentUrlDto dto = vnpayService.createPaymentUrl(session, request);
        return ResponseEntity.ok(dto);
    }

    // Bước 2: Verify & Capture sau khi User quay về từ VNPay
    // FE sẽ lấy toàn bộ query params trên URL gửi vào body (map)
    @PostMapping("/verify-and-capture")
    public ResponseEntity<CheckoutInvoiceDto> verifyAndCapture(
            @RequestParam Map<String, String> vnpParams,
            @Valid @RequestBody CheckoutCreateRequestDto requestDto,
            HttpSession session
    ) {
        log.info("VNPay return params: {}", vnpParams);
        CheckoutInvoiceDto dto = vnpayService.verifyAndCreateInvoice(vnpParams, requestDto, session);
        return ResponseEntity.ok(dto);
    }
}