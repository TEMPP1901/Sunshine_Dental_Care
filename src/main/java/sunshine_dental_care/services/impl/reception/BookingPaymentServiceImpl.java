package sunshine_dental_care.services.impl.reception;
import com.paypal.http.HttpResponse;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.orders.*;
import com.paypal.orders.ApplicationContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.services.huybro_checkout.paypal.services.client.PaypalApiClient;
import sunshine_dental_care.services.huybro_checkout.vnpay.services.client.VnpayConfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@SuppressWarnings("DuplicatedCode")
public class BookingPaymentServiceImpl {

    private final AppointmentRepo appointmentRepo;
    private final VnpayConfig vnpayConfig;
    private final PaypalApiClient paypalClient;

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.secret}")
    private String clientSecret;

    @Value("${paypal.base-url}")
    private String baseUrl;

    @Data
    @AllArgsConstructor
    public static class BookingPaypalResponse {
        private String orderId;
        private String approveUrl;
    }

    // ==========================================
    // PHẦN 1: VNPAY
    // ==========================================
    public String createVnpayUrl(Integer appointmentId, HttpServletRequest request) {
        // ... (Logic VNPAY giữ nguyên, mình ẩn đi cho gọn code) ...
        Appointment appt = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        if ("CANCELLED".equals(appt.getStatus())) throw new RuntimeException("BOOKING_EXPIRED");
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
        vnp_Params.put("vnp_ReturnUrl", "http://localhost:5173/booking/payment-result");
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));
        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
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
        String vnp_SecureHash = vnpParams.get("vnp_SecureHash");
        Map<String, String> paramsToHash = new HashMap<>(vnpParams);
        paramsToHash.remove("vnp_SecureHash");
        paramsToHash.remove("vnp_SecureHashType");
        String calculatedHash = vnpayConfig.hashAllFields(paramsToHash);
        if (!calculatedHash.equals(vnp_SecureHash)) throw new IllegalArgumentException("Invalid Checksum");
        if ("00".equals(vnpParams.get("vnp_ResponseCode"))) {
            String txnRef = vnpParams.get("vnp_TxnRef");
            String[] parts = txnRef.split("_");
            Integer appointmentId = Integer.parseInt(parts[1]);
            Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow();
            appt.setPaymentStatus("PAID");
            appt.setTransactionRef(vnpParams.get("vnp_TransactionNo"));
            if ("AWAITING_PAYMENT".equals(appt.getStatus())) appt.setStatus("CONFIRMED");
            appointmentRepo.save(appt);
        } else {
            throw new RuntimeException("Payment Failed");
        }
    }

    // ==========================================
    // PHẦN 2: PAYPAL
    // ==========================================

    private PayPalHttpClient getPaypalClient() {
        PayPalEnvironment environment;
        if (baseUrl != null && baseUrl.contains("sandbox")) {
            environment = new PayPalEnvironment.Sandbox(clientId, clientSecret);
        } else {
            environment = new PayPalEnvironment.Live(clientId, clientSecret);
        }
        return new PayPalHttpClient(environment);
    }

    public BookingPaypalResponse createPaypalOrder(Integer appointmentId) {
        Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow();

        if ("CANCELLED".equals(appt.getStatus())) {
            throw new RuntimeException("BOOKING_EXPIRED");
        }

        BigDecimal feeInUsd = appt.getBookingFee().divide(BigDecimal.valueOf(25000), 2, RoundingMode.HALF_UP);
        String invoiceCode = "BOOK_" + appointmentId + "_" + System.currentTimeMillis();

        OrderRequest orderRequest = new OrderRequest();

        orderRequest.checkoutPaymentIntent("CAPTURE");

        String myReturnUrl = "http://localhost:5173/booking/payment-result?appointmentId=" + appointmentId;

        ApplicationContext applicationContext = new ApplicationContext()
                .brandName("Sunshine Dental Care")
                .landingPage("BILLING")
                .returnUrl(myReturnUrl)
                .cancelUrl(myReturnUrl);

        orderRequest.applicationContext(applicationContext);

        List<PurchaseUnitRequest> purchaseUnitRequests = new ArrayList<>();
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
                .referenceId(invoiceCode)
                .amountWithBreakdown(new AmountWithBreakdown()
                        .currencyCode("USD")
                        .value(String.format(Locale.US, "%.2f", feeInUsd)));

        purchaseUnitRequests.add(purchaseUnitRequest);
        orderRequest.purchaseUnits(purchaseUnitRequests);

        OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);

        try {
            HttpResponse<Order> response = getPaypalClient().execute(request);
            Order order = response.result();

            String approveUrl = order.links().stream()
                    .filter(link -> "approve".equals(link.rel()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No approve link found"))
                    .href();

            return new BookingPaypalResponse(order.id(), approveUrl);

        } catch (IOException e) {
            throw new RuntimeException("PayPal Error: " + e.getMessage());
        }
    }

    @Transactional
    public void capturePaypalOrder(String orderId, Integer appointmentId) {
        var result = paypalClient.captureOrder(orderId);

        if ("COMPLETED".equalsIgnoreCase(result.getStatus())) {
            Appointment appt = appointmentRepo.findById(appointmentId).orElseThrow();
            appt.setPaymentStatus("PAID");
            appt.setTransactionRef(result.getCaptureId());

            if ("AWAITING_PAYMENT".equals(appt.getStatus())) {
                appt.setStatus("CONFIRMED");
            }
            appointmentRepo.save(appt);
        } else {
            throw new RuntimeException("PayPal Capture Failed");
        }
    }
}