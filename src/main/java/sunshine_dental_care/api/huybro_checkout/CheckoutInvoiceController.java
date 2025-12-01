package sunshine_dental_care.api.huybro_checkout;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutInvoiceService;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
@Slf4j
public class CheckoutInvoiceController {

    private final CheckoutInvoiceService checkoutInvoiceService;

    /**
     * Tạo hóa đơn từ giỏ hàng hiện tại.
     *
     * - COD:
     *   FE gọi trực tiếp endpoint này sau khi user xác nhận.
     *
     * - BANK_TRANSFER (PayPal):
     *   Sẽ dùng endpoint này sau khi đã xác nhận thanh toán thành công
     *   (ví dụ sau bước PayPal capture), tuỳ luồng bạn chọn.
     */
    @PostMapping("/invoices")
    public ResponseEntity<CheckoutInvoiceDto> createInvoice(
            @Valid @RequestBody CheckoutCreateRequestDto request,
            HttpSession session
    ) {
        log.info("[CheckoutInvoiceController] createInvoice, paymentType={}", request.getPaymentType());
        CheckoutInvoiceDto dto = checkoutInvoiceService.createInvoice(request, session);
        return ResponseEntity.ok(dto);
    }
}
