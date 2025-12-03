package sunshine_dental_care.services.huybro_checkout.paypal.services;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.huybro_cart.CartViewDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoice;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.services.huybro_cart.interfaces.CartService;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutInvoiceService;
import sunshine_dental_care.services.huybro_checkout.paypal.dto.PaypalCreateOrderResponseDto;
import sunshine_dental_care.services.huybro_checkout.paypal.services.client.PaypalApiClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaypalCheckoutServiceImpl implements PaypalCheckoutService {

    private final CartService cartService;
    private final CheckoutInvoiceService checkoutInvoiceService;
    private final ProductInvoiceRepository productInvoiceRepository;
    private final PaypalApiClient paypalApiClient;

    @Override
    @Transactional(readOnly = true)
    public PaypalCreateOrderResponseDto createOrderFromCart(HttpSession session) {
        CartViewDto cartView = cartService.getCartDetail(session);

        if (cartView == null || CollectionUtils.isEmpty(cartView.getItems())) {
            throw new IllegalStateException("Cart is empty");
        }

        BigDecimal total = cartView.getTotals() != null && cartView.getTotals().getTotalAfterTax() != null
                ? cartView.getTotals().getTotalAfterTax()
                : BigDecimal.ZERO;

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Total amount must be greater than zero");
        }

        String currency = cartView.getCurrency();
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        } else {
            currency = currency.toUpperCase();
        }
        if (!"USD".equals(currency)) {
            throw new IllegalStateException("PayPal payment is only allowed with USD currency");
        }

        String invoiceCode = cartView.getInvoiceCode();

        log.info("[PaypalCheckoutService] Create PayPal order, total={}, currency={}, invoiceCode={}",
                total, currency, invoiceCode);

        PaypalApiClient.CreateOrderResult result =
                paypalApiClient.createOrder(total, currency, invoiceCode);

        PaypalCreateOrderResponseDto dto = new PaypalCreateOrderResponseDto();
        dto.setOrderId(result.getOrderId());
        dto.setApproveUrl(result.getApproveUrl());
        dto.setTotalAmount(total);
        dto.setCurrency(currency);
        dto.setInvoiceCode(invoiceCode);

        return dto;
    }

    @Override
    @Transactional
    public CheckoutInvoiceDto captureOrderAndCreateInvoice(String paypalOrderId,
                                                           CheckoutCreateRequestDto request,
                                                           HttpSession session) {
        log.info("[PaypalCheckoutService] Capture PayPal order, orderId={}", paypalOrderId);

        PaypalApiClient.CaptureOrderResult captureResult =
                paypalApiClient.captureOrder(paypalOrderId);

        if (!"COMPLETED".equalsIgnoreCase(captureResult.getStatus())) {
            throw new IllegalStateException("PayPal payment is not completed");
        }

        request.setPaymentType("BANK_TRANSFER");
        request.setPaymentChannel("PAYPAL");

        CheckoutInvoiceDto invoiceDto = checkoutInvoiceService.createInvoice(request, session);

        ProductInvoice invoice = productInvoiceRepository.findById(invoiceDto.getInvoiceId())
                .orElseThrow(() -> new IllegalStateException("Invoice not found after creation"));

        invoice.setPaymentStatus("PAID");
        invoice.setPaymentReference(captureResult.getCaptureId());
        invoice.setPaymentCompletedAt(Instant.now());
        productInvoiceRepository.save(invoice);

        invoiceDto.setPaymentStatus(invoice.getPaymentStatus());
        invoiceDto.setPaymentReference(invoice.getPaymentReference());
        invoiceDto.setPaymentCompletedAt(invoice.getPaymentCompletedAt());

        if (invoiceDto.getCustomerEmail() == null
                && captureResult.getPayerEmail() != null) {
            invoice.setCustomerEmail(captureResult.getPayerEmail());
            productInvoiceRepository.save(invoice);
            invoiceDto.setCustomerEmail(captureResult.getPayerEmail());
        }

        return invoiceDto;
    }
}
