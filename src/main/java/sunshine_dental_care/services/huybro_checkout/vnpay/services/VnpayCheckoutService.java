package sunshine_dental_care.services.huybro_checkout.vnpay.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.services.huybro_checkout.vnpay.dto.VnpayPaymentUrlDto;

import java.util.Map;

public interface VnpayCheckoutService {
    VnpayPaymentUrlDto createPaymentUrl(HttpSession session, HttpServletRequest request);

    CheckoutInvoiceDto verifyAndCreateInvoice(Map<String, String> vnpParams,
                                              CheckoutCreateRequestDto requestDto,
                                              HttpSession session);
}