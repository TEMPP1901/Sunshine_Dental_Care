package sunshine_dental_care.api.reception.booking;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.services.impl.reception.BookingPaymentServiceImpl;

import java.util.Map;

@RestController
@RequestMapping("/api/booking/payment")
@RequiredArgsConstructor
public class BookingPaymentController {

    private final BookingPaymentServiceImpl paymentService;

    // 1. Tạo link VNPay
    // GET /api/booking/payment/vnpay/url?appointmentId=100
    @GetMapping("/vnpay/url")
    public ResponseEntity<?> getVnpayUrl(@RequestParam Integer appointmentId, HttpServletRequest request) {
        String url = paymentService.createVnpayUrl(appointmentId, request);
        return ResponseEntity.ok(Map.of("paymentUrl", url));
    }

    // 2. Verify VNPay Return (Frontend gọi cái này sau khi VNPay redirect về)
    // POST /api/booking/payment/vnpay/verify
    @PostMapping("/vnpay/verify")
    public ResponseEntity<?> verifyVnpay(@RequestBody Map<String, String> vnpParams) {
        try {
            paymentService.processVnpayCallback(vnpParams);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", e.getMessage()));
        }
    }

    // 3. Tạo PayPal Order
    @PostMapping("/paypal/create")
    public ResponseEntity<?> createPaypalOrder(@RequestParam Integer appointmentId) {
        var result = paymentService.createPaypalOrder(appointmentId);
        return ResponseEntity.ok(result); // Trả về orderId và approveUrl
    }

    // 4. Capture PayPal
    @PostMapping("/paypal/capture")
    public ResponseEntity<?> capturePaypal(@RequestParam String orderId, @RequestParam Integer appointmentId) {
        try {
            paymentService.capturePaypalOrder(orderId, appointmentId);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED"));
        }
    }
}