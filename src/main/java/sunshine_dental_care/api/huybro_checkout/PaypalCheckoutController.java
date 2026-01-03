package sunshine_dental_care.api.huybro_checkout;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.services.huybro_checkout.paypal.dto.PaypalCreateOrderResponseDto;
import sunshine_dental_care.services.huybro_checkout.paypal.services.PaypalCheckoutService;


@RestController
@RequestMapping("/api/checkout/paypal")
@RequiredArgsConstructor
@Slf4j
public class PaypalCheckoutController {

    private final PaypalCheckoutService paypalCheckoutService;

    /**
     * Bước 1: User chọn Bank Transfer → PayPal, FE ấn nút "Pay with PayPal"
     * → FE gọi endpoint này.
     *
     * BE sẽ:
     *  - Đọc cart hiện tại
     *  - Gọi PayPal tạo order
     *  - Trả về approveUrl cho FE redirect
     */
    @PostMapping("/create-order")
    public ResponseEntity<PaypalCreateOrderResponseDto> createOrder(HttpSession session) {
        PaypalCreateOrderResponseDto dto = paypalCheckoutService.createOrderFromCart(session);
        return ResponseEntity.ok(dto);
    }

    /**
     * Bước 2: Sau khi thanh toán thành công trên PayPal,
     * PayPal redirect về FE với ?token={orderId}.
     *
     * FE cho user xem màn hình "We have received your payment" + nút "Confirm".
     * Khi user ấn nút đó:
     *  - FE gửi token (orderId) + thông tin địa chỉ/phone
     *  - BE capture PayPal order + tạo ProductInvoice
     *  - Trả về CheckoutInvoiceDto để FE show hóa đơn chuyển khoản.
     */
    @PostMapping("/capture")
    public ResponseEntity<CheckoutInvoiceDto> captureAndCreateInvoice(
            @RequestParam("token") String paypalOrderId,
            @Valid @RequestBody CheckoutCreateRequestDto request,
            HttpSession session
    ) {
        log.info("[PaypalCheckoutController] capture, orderId={}", paypalOrderId);
        CheckoutInvoiceDto dto =
                paypalCheckoutService.captureOrderAndCreateInvoice(paypalOrderId, request, session);
        return ResponseEntity.ok(dto);
    }
}
