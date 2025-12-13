package sunshine_dental_care.services.impl.reception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.repositories.reception.AppointmentRepo; // Hoặc repo tương ứng
import sunshine_dental_care.services.huybro_checkout.paypal.services.client.PaypalApiClient;
import sunshine_dental_care.services.huybro_checkout.vnpay.services.client.VnpayConfig;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookingPaymentServiceImpl {

    private final AppointmentRepo appointmentRepo;
    private final VnpayConfig vnpayConfig;
    private final PaypalApiClient paypalClient;

    // ==========================================
    // PHẦN 1: VNPAY (Nội địa)
    // ==========================================
    public String createVnpayUrl(Integer appointmentId, HttpServletRequest request) {
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if ("CANCELLED".equals(appt.getStatus())) {
            throw new RuntimeException("BOOKING_EXPIRED"); // Keyword để FE bắt lỗi
        }
        // Lấy số tiền cọc (Lưu ý: VNPay yêu cầu đơn vị là đồng, không có dấu phẩy, nhân 100)
        // Ví dụ: 1.000.000 VNĐ -> 100000000
        long amount = appt.getBookingFee().multiply(BigDecimal.valueOf(100)).longValue();

        String vnp_TxnRef = "BOOK_" + appointmentId + "_" + System.currentTimeMillis();
        String vnp_IpAddr = vnpayConfig.getIpAddress(request);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnpayConfig.getVnp_Version());
        vnp_Params.put("vnp_Command", vnpayConfig.getVnp_Command());
        vnp_Params.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Dat coc lich hen #" + appointmentId);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");

        // URL trả về: Bạn cần tạo 1 trang React riêng để đón kết quả Booking
        // Ví dụ: http://localhost:5173/booking/payment-result
        vnp_Params.put("vnp_ReturnUrl", "http://localhost:5173/booking/payment-result");
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        // Thời gian
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));
        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        // Build URL (Đoạn này copy logic hash từ VnpayCheckoutServiceImpl qua)
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = vnpayConfig.hmacSHA512(vnpayConfig.getSecretKey(), hashData.toString());
        return vnpayConfig.getVnp_PayUrl() + "?" + queryUrl + "&vnp_SecureHash=" + vnp_SecureHash;
    }

    @Transactional
    public void processVnpayCallback(Map<String, String> vnpParams) {
        // 1. Checksum (Security check)
        String vnp_SecureHash = vnpParams.get("vnp_SecureHash");
        Map<String, String> paramsToHash = new HashMap<>(vnpParams);
        paramsToHash.remove("vnp_SecureHash");
        paramsToHash.remove("vnp_SecureHashType");

        String calculatedHash = vnpayConfig.hashAllFields(paramsToHash);
        if (!calculatedHash.equals(vnp_SecureHash)) {
            throw new IllegalArgumentException("Invalid Checksum");
        }

        // 2. Kiểm tra thành công (ResponseCode = 00)
        if ("00".equals(vnpParams.get("vnp_ResponseCode"))) {
            // Parse ID từ TxnRef (Format: BOOK_{id}_{timestamp})
            String txnRef = vnpParams.get("vnp_TxnRef");
            String[] parts = txnRef.split("_");
            Integer appointmentId = Integer.parseInt(parts[1]);

            // 3. Cập nhật DB
            Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow();
            appt.setPaymentStatus("PAID");
            appt.setTransactionRef(vnpParams.get("vnp_TransactionNo")); // Mã giao dịch VNPay

            // Check trạng thái 'AWAITING_PAYMENT' (trạng thái chờ lúc mới đặt)
            if ("AWAITING_PAYMENT".equals(appt.getStatus())) {
                // Chuyển sang 'PENDING' để hiện lên Dashboard cho lễ tân thấy
                appt.setStatus("PENDING");
            }

            appointmentRepo.save(appt);
        } else {
            throw new RuntimeException("Payment Failed");
        }
    }

    // ==========================================
    // PHẦN 2: PAYPAL (Quốc tế)
    // ==========================================
    public PaypalApiClient.CreateOrderResult createPaypalOrder(Integer appointmentId) {
        Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow();

        // PayPal cần Invoice ID unique
        String invoiceCode = "BOOK_" + appointmentId + "_" + System.currentTimeMillis();

        if ("CANCELLED".equals(appt.getStatus())) {
            throw new RuntimeException("BOOKING_EXPIRED");
        }
        // Gọi client có sẵn
        // Lưu ý: PayPal Sandbox test bằng USD. Nếu bookingFee là VND cần / 25000
        BigDecimal feeInUsd = appt.getBookingFee().divide(BigDecimal.valueOf(25000));

        return paypalClient.createOrder(feeInUsd, "USD", invoiceCode);
    }

    @Transactional
    public void capturePaypalOrder(String orderId, Integer appointmentId) {
        // Gọi client capture
        PaypalApiClient.CaptureOrderResult result = paypalClient.captureOrder(orderId);

        if ("COMPLETED".equalsIgnoreCase(result.getStatus())) {
            Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow();
            appt.setPaymentStatus("PAID");
            appt.setTransactionRef(result.getCaptureId());
            // Check trạng thái 'AWAITING_PAYMENT'
            if ("AWAITING_PAYMENT".equals(appt.getStatus())) {
                // Chuyển sang 'PENDING' để hiện lên Dashboard
                appt.setStatus("PENDING");
            }
            appointmentRepo.save(appt);
        } else {
            throw new RuntimeException("PayPal Capture Failed");
        }
    }
}
