package sunshine_dental_care.services.huybro_checkout.interfaces;

import jakarta.servlet.http.HttpSession;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;

public interface CheckoutInvoiceService {

    CheckoutInvoiceDto createInvoice(CheckoutCreateRequestDto request, HttpSession session);
}
