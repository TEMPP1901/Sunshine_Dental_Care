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
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoice;
import sunshine_dental_care.entities.huybro_product_invoices.ProductInvoiceItem;
import sunshine_dental_care.entities.huybro_products.Product;
import sunshine_dental_care.entities.huybro_product_inventories.ProductInventory;
import sunshine_dental_care.exceptions.huy_bro_checkoutLog.CheckoutValidationException;
import sunshine_dental_care.repositories.huybro_custom.ClinicRepository; // [IMPORT MỚI]
import sunshine_dental_care.repositories.huybro_custom.UserCustomRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInventoryRepository; // [IMPORT MỚI]
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceItemRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInvoiceRepository;
import sunshine_dental_care.repositories.huybro_products.ProductRepository;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.huybro_cart.impl.CurrencyRateInternalService;
import sunshine_dental_care.services.huybro_cart.interfaces.CartService;
import sunshine_dental_care.services.huybro_checkout.email.client.EmailService;
import sunshine_dental_care.services.huybro_checkout.interfaces.CheckoutInvoiceService;
import sunshine_dental_care.services.interfaces.system.AuditLogService;
import sunshine_dental_care.utils.huybro_utils.EmailTemplateUtils;

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
    private final CurrencyRateInternalService currencyRateService;
    private final ProductInventoryRepository productInventoryRepository;
    private final ClinicRepository clinicRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    // [CONFIG HARDCODE] Theo yêu cầu: Q9 ưu tiên, Q1 dự phòng
    private static final Integer ID_Q1 = 1; // Kho Khám
    private static final Integer ID_Q9 = 2; // Kho Bán (Ưu tiên)

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

        // BƯỚC 1: Tạo hộp, bỏ số 1 vào trước (Giả định là USD)
        BigDecimal exchangeRate = BigDecimal.ONE;

        // BƯỚC 2: Kiểm tra xem có phải VND không?
        if ("VND".equalsIgnoreCase(finalCurrency)) {
            // Nếu là VND -> Gọi API lấy số 25000 -> Bỏ vào hộp (Đè lên số 1 cũ)
            exchangeRate = currencyRateService.getRate("USD", "VND");
        }

        // 2. Identify User & Create Item Note Tag
        boolean isLoggedIn = false;
        User currentUser = null;
        String itemNoteTag;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser currentUserPrincipal) {
            Integer userId = currentUserPrincipal.userId();
            currentUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            isLoggedIn = true;
            itemNoteTag = String.format("[MEMBER: ID #%d]", userId);
        } else {
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
        String customerFullName, customerEmail, customerPhone, shippingAddress = trimToNull(request.getShippingAddress()), invoiceUserNote = trimToNull(request.getNote());
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
        if (shippingAddress == null || shippingAddress.length() < 3) errors.put("shippingAddress", "Shipping address is required (min 3 chars)");
        if (!isLoggedIn && "BANK_TRANSFER".equals(paymentType)) {
            if (customerFullName == null) errors.put("customerFullName", "Full name is required");
            if (customerEmail == null) errors.put("customerEmail", "Email is required");
            if (customerPhone == null) errors.put("customerPhone", "Phone number is required");
        }
        if (!errors.isEmpty()) throw new CheckoutValidationException(errors);

        // =========================================================
        // CREATE INVOICE
        // =========================================================

        BigDecimal subTotal = cartView.getTotals().getSubTotalBeforeTax();
        BigDecimal totalAfterTax = cartView.getTotals().getTotalAfterTax();
        BigDecimal taxTotal = totalAfterTax.subtract(subTotal).max(BigDecimal.ZERO);

        // [LOGIC MỚI] Gắn Clinic xuất hàng (Mặc định là Q9 - Nơi bán online chính)
        Clinic saleClinic = clinicRepository.findById(ID_Q9)
                .orElseThrow(() -> new RuntimeException("Config Error: Clinic Q9 not found"));

        ProductInvoice invoice = new ProductInvoice();
        invoice.setClinic(saleClinic); // Gắn Clinic
        invoice.setInvoiceCode(cartView.getInvoiceCode());
        invoice.setSubTotal(subTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxTotal(taxTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(totalAfterTax.setScale(2, RoundingMode.HALF_UP));
        invoice.setCurrency(finalCurrency);
        invoice.setExchangeRate(exchangeRate);

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

        // === VÒNG LẶP TRỪ KHO ƯU TIÊN ===
        for (CartItemDto cartItem : cartView.getItems()) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Product not found for id=" + cartItem.getProductId()));

            int quantityNeeded = cartItem.getQuantity();

            // 1. Lấy Inventory hiện tại
            ProductInventory invQ9 = getInventoryOrNew(product, ID_Q9);
            ProductInventory invQ1 = getInventoryOrNew(product, ID_Q1);

            int stockQ9 = invQ9.getQuantity();
            int stockQ1 = invQ1.getQuantity();

            // 2. Double Check: Tổng kho có đủ không?
            if (stockQ9 + stockQ1 < quantityNeeded) {
                throw new IllegalStateException("Out of stock: Product " + product.getProductName());
            }

            // 3. Tính toán trừ kho (Q9 trước, Q1 sau)
            int takeFromQ9 = 0;
            int takeFromQ1 = 0;

            if (stockQ9 >= quantityNeeded) {
                // Case A: Q9 đủ -> Lấy hết từ Q9
                takeFromQ9 = quantityNeeded;
            } else {
                // Case B: Q9 thiếu -> Lấy hết Q9, bù phần thiếu từ Q1
                takeFromQ9 = stockQ9;
                takeFromQ1 = quantityNeeded - stockQ9;
            }

            // 4. Update DB
            if (takeFromQ9 > 0) {
                invQ9.setQuantity(invQ9.getQuantity() - takeFromQ9);
                invQ9.setLastUpdated(now);
                productInventoryRepository.save(invQ9);
                log.info("Deducted {} items from Q9 for Product {}", takeFromQ9, product.getId());
            }

            if (takeFromQ1 > 0) {
                invQ1.setQuantity(invQ1.getQuantity() - takeFromQ1);
                invQ1.setLastUpdated(now);
                productInventoryRepository.save(invQ1);
                log.info("Deducted {} items from Q1 for Product {}", takeFromQ1, product.getId());
            }

            // 5. Sync lại Tổng Unit và Active
            syncProductTotalUnit(product);

            // 6. Tạo Invoice Item
            ProductInvoiceItem item = new ProductInvoiceItem();
            item.setInvoice(savedInvoice);
            item.setProduct(product);
            item.setQuantity(quantityNeeded);
            item.setUnitPriceBeforeTax(cartItem.getUnitPriceBeforeTax());
            item.setTaxRatePercent(cartItem.getTaxRatePercent());
            item.setTaxAmount(cartItem.getTaxAmount());
            item.setLineTotalAmount(cartItem.getLineTotalAmount());
            item.setProductNameSnapshot(cartItem.getProductName());
            item.setSkuSnapshot(cartItem.getSku());
            item.setNote(itemNoteTag);
            item.setCreatedAt(now);

            // Ghi nhận lại tồn kho của kho chính (Q9) để tham khảo
            item.setRemainingQuantityAfterSale(invQ9.getQuantity());

            invoiceItems.add(item);
        }

        productInvoiceItemRepository.saveAll(invoiceItems);
        cartService.clearCart(session);
        try {
            if (savedInvoice.getCustomerEmail() != null) {
                String subject = "Xác nhận đơn hàng #" + savedInvoice.getInvoiceCode();
                // Dùng chung 1 hàm buildInvoiceEmail
                String htmlBody = EmailTemplateUtils.buildInvoiceEmail(savedInvoice, invoiceItems);
                emailService.sendHtmlEmail(savedInvoice.getCustomerEmail(), subject, htmlBody);
            }
        } catch (Exception e) {
            log.warn("Failed to send order email: {}", e.getMessage());
        }

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

    // [HELPER MỚI] Lấy inventory, nếu không có trả về object ảo qty=0
    private ProductInventory getInventoryOrNew(Product product, Integer clinicId) {
        return productInventoryRepository.findByProductIdAndClinicId(product.getId(), clinicId)
                .orElseGet(() -> {
                    ProductInventory dummy = new ProductInventory();
                    dummy.setProduct(product);
                    dummy.setQuantity(0);
                    return dummy;
                });
    }

    // [HELPER MỚI] Đồng bộ tổng
    private void syncProductTotalUnit(Product product) {
        Integer totalUnit = productInventoryRepository.sumTotalQuantityByProductId(product.getId());
        product.setUnit(totalUnit);
        if (totalUnit == 0) {
            product.setIsActive(false);
        }
        productRepository.save(product);
    }

    private CheckoutInvoiceDto mapToCheckoutInvoiceDto(ProductInvoice invoice,
                                                       List<ProductInvoiceItem> items) {
        // ... (Logic Mapping giữ nguyên) ...
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