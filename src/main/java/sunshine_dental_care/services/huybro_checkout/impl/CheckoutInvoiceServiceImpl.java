package sunshine_dental_care.services.huybro_checkout.impl;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import sunshine_dental_care.dto.huybro_cart.CartItemDto;
import sunshine_dental_care.dto.huybro_cart.CartViewDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutCreateRequestDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceDto;
import sunshine_dental_care.dto.huybro_checkout.CheckoutInvoiceItemDto;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoice;
import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoiceItem;
import sunshine_dental_care.entities.huybro_products.Product;
import sunshine_dental_care.exceptions.huy_bro_checkoutLog.CheckoutValidationException;
import sunshine_dental_care.repositories.huybro_custom.UserCustomRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceItemRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.repositories.huybro_products.ProductRepository;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.huybro_cart.interfaces.CartService;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutInvoiceService;
import sunshine_dental_care.services.interfaces.system.AuditLogService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutInvoiceServiceImpl implements CheckoutInvoiceService {

    private final CartService cartService;
    private final ProductRepository productRepository;
    private final ProductInvoiceRepository productInvoiceRepository;
    private final ProductInvoiceItemRepository productInvoiceItemRepository;
    private final UserCustomRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public CheckoutInvoiceDto createInvoice(CheckoutCreateRequestDto request, HttpSession session) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, String> errors = new HashMap<>();

        // 1. Currency & Cart
        String currencyRequest = normalize(request.getCurrency());
        CartViewDto cartView = cartService.getCartPreviewByCurrency(session, currencyRequest);

        if (cartView == null || CollectionUtils.isEmpty(cartView.getItems())) {
            throw new IllegalStateException("Cart is empty");
        }
        String finalCurrency = cartView.getCurrency();

        // 2. Identify User & Create Item Note Tag
        boolean isLoggedIn = false;
        User currentUser = null;
        String itemNoteTag; // Biến này để lưu vào ProductInvoiceItem

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser currentUserPrincipal) {
            Integer userId = currentUserPrincipal.userId();
            currentUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            isLoggedIn = true;
            // Tag cho Member
            itemNoteTag = String.format("[MEMBER: ID #%d]", userId);
        } else {
            // Tag cho Guest
            itemNoteTag = "[POTENTIAL]";
        }

        // 3. Payment Validation
        String paymentType = normalize(request.getPaymentType());
        if (!"COD".equals(paymentType) && !"BANK_TRANSFER".equals(paymentType)) {
            throw new IllegalArgumentException("Unsupported payment type");
        }
        if ("COD".equals(paymentType) && !isLoggedIn) {
            throw new IllegalArgumentException("Guest users cannot use COD. Please login to continue.");
        }

        String paymentChannel = normalize(request.getPaymentChannel());
        if (paymentChannel == null) {
            paymentChannel = "COD".equals(paymentType) ? "CASH_ON_DELIVERY" : "PAYPAL";
        }

        if ("PAYPAL".equals(paymentChannel) && !"USD".equals(finalCurrency)) {
            throw new IllegalArgumentException("PAYPAL payment requires USD currency.");
        }
        if ("VNPAY".equals(paymentChannel) && !"VND".equals(finalCurrency)) {
            throw new IllegalArgumentException("VNPAY payment requires VND currency.");
        }

        // 4. Customer Info
        String customerFullName;
        String customerEmail;
        String customerPhone;
        String shippingAddress = trimToNull(request.getShippingAddress());
        String invoiceUserNote = trimToNull(request.getNote());

        if (isLoggedIn) {
            customerFullName = trimToNull(currentUser.getFullName());
            customerEmail = trimToNull(currentUser.getEmail());
            String phoneFromRequest = trimToNull(request.getCustomerPhone());
            customerPhone = phoneFromRequest != null ? phoneFromRequest : trimToNull(currentUser.getPhone());
        } else {
            customerFullName = trimToNull(request.getCustomerFullName());
            customerEmail = trimToNull(request.getCustomerEmail());
            customerPhone = trimToNull(request.getCustomerPhone());
        }

        // 5. Validation Logic
        if (shippingAddress == null || shippingAddress.length() < 3) {
            errors.put("shippingAddress", "Shipping address is required (min 3 chars)");
        }
        if (!isLoggedIn) {
            if ("BANK_TRANSFER".equals(paymentType)) {
                if (customerFullName == null) errors.put("customerFullName", "Full name is required");
                if (customerEmail == null) errors.put("customerEmail", "Email is required");
                if (customerPhone == null) errors.put("customerPhone", "Phone number is required");
            }
        }

        if (!errors.isEmpty()) {
            throw new CheckoutValidationException(errors);
        }

        // =========================================================
        // CREATE INVOICE
        // =========================================================

        BigDecimal subTotal = cartView.getTotals().getSubTotalBeforeTax();
        BigDecimal totalAfterTax = cartView.getTotals().getTotalAfterTax();
        BigDecimal taxTotal = totalAfterTax.subtract(subTotal).max(BigDecimal.ZERO);

        ProductInvoice invoice = new ProductInvoice();
        invoice.setInvoiceCode(cartView.getInvoiceCode());
        invoice.setSubTotal(subTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxTotal(taxTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(totalAfterTax.setScale(2, RoundingMode.HALF_UP));
        invoice.setCurrency(finalCurrency);

        invoice.setPaymentMethod(paymentType);
        invoice.setPaymentChannel(paymentChannel);
        invoice.setPaymentStatus("PENDING");
        invoice.setInvoiceStatus("NEW");

        invoice.setCustomerFullName(customerFullName);
        invoice.setCustomerEmail(customerEmail);
        invoice.setCustomerPhone(customerPhone);
        invoice.setShippingAddress(shippingAddress);
        invoice.setNotes(invoiceUserNote);

        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);

        ProductInvoice savedInvoice = productInvoiceRepository.save(invoice);

        List<ProductInvoiceItem> invoiceItems = new ArrayList<>();

        for (CartItemDto cartItem : cartView.getItems()) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Product not found for id=" + cartItem.getProductId()));

            ProductInvoiceItem item = new ProductInvoiceItem();
            item.setInvoice(savedInvoice);
            item.setProduct(product);
            item.setQuantity(cartItem.getQuantity());
            item.setUnitPriceBeforeTax(cartItem.getUnitPriceBeforeTax());
            item.setTaxRatePercent(cartItem.getTaxRatePercent());
            item.setTaxAmount(cartItem.getTaxAmount());
            item.setLineTotalAmount(cartItem.getLineTotalAmount());
            item.setProductNameSnapshot(cartItem.getProductName());
            item.setSkuSnapshot(cartItem.getSku());
            item.setNote(itemNoteTag);

            item.setCreatedAt(now);

            if (product.getUnit() != null) {
                int remaining = product.getUnit() - cartItem.getQuantity();
                if (remaining < 0) {
                    throw new IllegalStateException("Not enough stock for product: " + product.getProductName());
                }
                item.setRemainingQuantityAfterSale(remaining);

                product.setUnit(remaining);
                if (remaining == 0) {
                    product.setIsActive(false);
                    log.info("Product id={} has reached 0 stock. Auto-deactivating.", product.getId());
                }
                productRepository.save(product);
            } else {
                item.setRemainingQuantityAfterSale(null);
            }
            invoiceItems.add(item);
        }

        productInvoiceItemRepository.saveAll(invoiceItems);
        cartService.clearCart(session);

        // Ghi audit log tạo hóa đơn
        User actor = resolveCurrentUserEntity();
        if (actor != null) {
            auditLogService.logAction(
                    actor,
                    "CREATE_INVOICE",
                    "PRODUCT_INVOICE",
                    savedInvoice.getId(),
                    null,
                    String.format("Invoice %s total %s %s via %s/%s",
                            savedInvoice.getInvoiceCode(),
                            savedInvoice.getTotalAmount(),
                            savedInvoice.getCurrency(),
                            savedInvoice.getPaymentMethod(),
                            savedInvoice.getPaymentChannel()));
        }

        return mapToCheckoutInvoiceDto(savedInvoice, invoiceItems);
    }

    private CheckoutInvoiceDto mapToCheckoutInvoiceDto(ProductInvoice invoice,
                                                       List<ProductInvoiceItem> items) {
        CheckoutInvoiceDto dto = new CheckoutInvoiceDto();

        dto.setInvoiceId(invoice.getId());
        dto.setInvoiceCode(invoice.getInvoiceCode());
        dto.setSubTotal(invoice.getSubTotal());
        dto.setTaxTotal(invoice.getTaxTotal());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setCurrency(invoice.getCurrency());

        dto.setPaymentStatus(invoice.getPaymentStatus());
        dto.setInvoiceStatus(invoice.getInvoiceStatus());

        dto.setPaymentMethod(invoice.getPaymentMethod());
        dto.setPaymentChannel(invoice.getPaymentChannel());
        dto.setPaymentCompletedAt(invoice.getPaymentCompletedAt());

        dto.setCreatedAt(invoice.getCreatedAt());
        dto.setUpdatedAt(invoice.getUpdatedAt());

        dto.setCustomerFullName(invoice.getCustomerFullName());
        dto.setCustomerEmail(invoice.getCustomerEmail());
        dto.setCustomerPhone(invoice.getCustomerPhone());
        dto.setShippingAddress(invoice.getShippingAddress());
        dto.setPaymentReference(invoice.getPaymentReference());
        dto.setNotes(invoice.getNotes());

        List<CheckoutInvoiceItemDto> itemDtos = new ArrayList<>();
        for (ProductInvoiceItem item : items) {
            CheckoutInvoiceItemDto itemDto = new CheckoutInvoiceItemDto();
            itemDto.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
            itemDto.setProductName(item.getProductNameSnapshot());
            itemDto.setSku(item.getSkuSnapshot());
            itemDto.setQuantity(item.getQuantity());
            itemDto.setUnitPriceBeforeTax(item.getUnitPriceBeforeTax());
            itemDto.setTaxRatePercent(item.getTaxRatePercent());
            itemDto.setTaxAmount(item.getTaxAmount());
            itemDto.setLineTotalAmount(item.getLineTotalAmount());
            itemDto.setRemainingQuantityAfterSale(item.getRemainingQuantityAfterSale());
            itemDtos.add(itemDto);
        }

        dto.setItems(itemDtos);
        return dto;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.trimToNull(value);
    }

    // Helper: lấy actor từ SecurityContext để ghi audit log
    private User resolveCurrentUserEntity() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser currentUser) {
                Integer userId = currentUser.userId();
                return userRepository.findById(userId).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Cannot resolve current user for audit log: {}", e.getMessage());
        }
        return null;
    }
}