package sunshine_dental_care.services.huybro_checkout.vnpay.services.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import sunshine_dental_care.dto.huybro_cart.CartViewDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoice;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.services.huybro_cart.interfaces.CartService;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutInvoiceService;
import sunshine_dental_care.services.huybro_checkout.vnpay.dto.VnpayPaymentUrlDto;
import sunshine_dental_care.services.huybro_checkout.vnpay.services.VnpayCheckoutService;
import sunshine_dental_care.services.huybro_checkout.vnpay.services.client.VnpayConfig;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayCheckoutServiceImpl implements VnpayCheckoutService {

    private final VnpayConfig vnpayConfig;
    private final CartService cartService;
    private final CheckoutInvoiceService checkoutInvoiceService;
    private final ProductInvoiceRepository productInvoiceRepository;

    @Override
    public VnpayPaymentUrlDto createPaymentUrl(HttpSession session, HttpServletRequest request) {
        // 1. Lấy thông tin Cart
        CartViewDto cart = cartService.getCartPreviewByCurrency(session, "VND");
        if (cart == null || CollectionUtils.isEmpty(cart.getItems())) {
            throw new IllegalStateException("Cart is empty");
        }

        // 2. Validate Tiền tệ (VNPay bắt buộc VND)
        String currency = cart.getCurrency();
        if (!"VND".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("VNPay only supports VND currency.");
        }

        // 3. Tính tiền (VNPay yêu cầu số tiền * 100, ví dụ 10000 VND -> 1000000)
        BigDecimal total = cart.getTotals().getTotalAfterTax();
        long amount = total.multiply(BigDecimal.valueOf(100)).longValue();

        // 4. Tạo tham số VNPay
        String vnp_TxnRef = cart.getInvoiceCode() + "_" + System.currentTimeMillis(); // Mã giao dịch unique
        String vnp_IpAddr = vnpayConfig.getIpAddress(request);
        String vnp_TmnCode = vnpayConfig.getTmnCode();

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnpayConfig.getVnp_Version());
        vnp_Params.put("vnp_Command", vnpayConfig.getVnp_Command());
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", "Thanh toan don hang " + cart.getInvoiceCode());
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpayConfig.getVnp_ReturnUrl());
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        // Thời gian tạo
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        // Thời gian hết hạn (15 phút)
        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // 5. Build URL & Checksum
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                // Build query
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
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        String paymentUrl = vnpayConfig.getVnp_PayUrl() + "?" + queryUrl;

        return new VnpayPaymentUrlDto(paymentUrl);
    }

    @Override
    @Transactional
    public CheckoutInvoiceDto verifyAndCreateInvoice(Map<String, String> vnpParams,
                                                     CheckoutCreateRequestDto requestDto,
                                                     HttpSession session) {
        // 1. Kiểm tra checksum để đảm bảo dữ liệu không bị sửa đổi
        String vnp_SecureHash = vnpParams.get("vnp_SecureHash");
        if (vnp_SecureHash == null) {
            throw new IllegalArgumentException("Checksum missing");
        }

        // Loại bỏ secureHash khỏi map để tính lại hash
        Map<String, String> paramsToHash = new HashMap<>(vnpParams);
        paramsToHash.remove("vnp_SecureHash");
        paramsToHash.remove("vnp_SecureHashType"); // Nếu có

        String calculatedHash = vnpayConfig.hashAllFields(paramsToHash);
        if (!calculatedHash.equals(vnp_SecureHash)) {
            throw new IllegalArgumentException("Invalid Checksum");
        }

        // 2. Kiểm tra trạng thái giao dịch
        // vnp_ResponseCode = '00' là thành công
        String responseCode = vnpParams.get("vnp_ResponseCode");
        if (!"00".equals(responseCode)) {
            throw new IllegalArgumentException("VNPay payment failed with code: " + responseCode);
        }

        // 3. Nếu thành công -> Tạo Invoice (Trừ kho, lưu DB)
        requestDto.setPaymentType("BANK_TRANSFER");
        requestDto.setPaymentChannel("VNPAY");
        requestDto.setCurrency("VND");

        CheckoutInvoiceDto invoiceDto = checkoutInvoiceService.createInvoice(requestDto, session);

        // 4. Update trạng thái thanh toán
        ProductInvoice invoice = productInvoiceRepository.findById(invoiceDto.getInvoiceId())
                .orElseThrow(() -> new IllegalStateException("Invoice not found after creation"));

        invoice.setPaymentStatus("PAID");
        invoice.setPaymentReference(vnpParams.get("vnp_TransactionNo")); // Mã giao dịch tại VNPay
        invoice.setPaymentCompletedAt(Instant.now());
        productInvoiceRepository.save(invoice);

        invoiceDto.setPaymentStatus("PAID");
        invoiceDto.setPaymentReference(invoice.getPaymentReference());
        invoiceDto.setPaymentCompletedAt(invoice.getPaymentCompletedAt());

        return invoiceDto;
    }
}