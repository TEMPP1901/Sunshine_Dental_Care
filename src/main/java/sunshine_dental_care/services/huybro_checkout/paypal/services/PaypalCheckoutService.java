package sunshine_dental_care.services.huybro_checkout.paypal.services;

import jakarta.servlet.http.HttpSession;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.services.huybro_checkout.paypal.dto.PaypalCreateOrderResponseDto;

public interface PaypalCheckoutService {

    PaypalCreateOrderResponseDto createOrderFromCart(HttpSession session);

    CheckoutInvoiceDto captureOrderAndCreateInvoice(String paypalOrderId,
                                                    CheckoutCreateRequestDto request,
                                                    HttpSession session);
}