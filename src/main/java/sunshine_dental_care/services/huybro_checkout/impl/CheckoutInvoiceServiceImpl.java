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

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Override
    @Transactional
    public CheckoutInvoiceDto createInvoice(CheckoutCreateRequestDto request, HttpSession session) {
        // 1. Khởi tạo Map chứa lỗi
        Map<String, String> errors = new HashMap<>();

        // 2. Validate Giỏ hàng (Lỗi logic blocking)
        CartViewDto cartView = cartService.getCartDetail(session);
        if (cartView == null || CollectionUtils.isEmpty(cartView.getItems())) {
            throw new IllegalStateException("Cart is empty");
        }

        // 3. Xác định User
        boolean isLoggedIn = false;
        User currentUser = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser currentUserPrincipal) {
            Integer userId = currentUserPrincipal.userId();
            currentUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            isLoggedIn = true;
        }

        // 4. Chuẩn hóa dữ liệu
        String paymentType = normalize(request.getPaymentType());
        if (!"COD".equals(paymentType) && !"BANK_TRANSFER".equals(paymentType)) {
            throw new IllegalArgumentException("Unsupported payment type");
        }

        // [RULE] Guest (N) cannot use COD -> Chặn ngay lập tức vì đây là lỗi logic luồng
        if ("COD".equals(paymentType) && !isLoggedIn) {
            throw new IllegalArgumentException("Guest users cannot use COD. Please login to continue.");
        }

        String paymentChannel = normalize(request.getPaymentChannel());
        if (paymentChannel == null) {
            paymentChannel = "COD".equals(paymentType) ? "CASH_ON_DELIVERY" : "PAYPAL";
        }

        String currency = normalize(request.getCurrency());
        if (currency == null) currency = normalize(cartView.getCurrency());
        if (currency == null) currency = "USD";

        // [RULE] Ràng buộc Tiền tệ - Kênh thanh toán
        if ("PAYPAL".equals(paymentChannel) && !"USD".equals(currency)) {
            throw new IllegalArgumentException("PAYPAL payment requires USD currency.");
        }
        if ("VNPAY".equals(paymentChannel) && !"VND".equals(currency)) {
            throw new IllegalArgumentException("VNPAY payment requires VND currency.");
        }

        // 5. Chuẩn bị dữ liệu khách hàng & Validate chi tiết
        String customerFullName;
        String customerEmail;
        String customerPhone;
        String shippingAddress = trimToNull(request.getShippingAddress());
        String note = trimToNull(request.getNote());

        if (isLoggedIn) {
            // User đã đăng nhập: Lấy từ DB
            customerFullName = trimToNull(currentUser.getFullName());
            customerEmail = trimToNull(currentUser.getEmail());
            // Phone: Ưu tiên form gửi lên (nếu user sửa sđt nhận hàng), fallback về DB
            String phoneFromRequest = trimToNull(request.getCustomerPhone());
            customerPhone = phoneFromRequest != null ? phoneFromRequest : trimToNull(currentUser.getPhone());
        } else {
            // Guest: Lấy hoàn toàn từ Form
            customerFullName = trimToNull(request.getCustomerFullName());
            customerEmail = trimToNull(request.getCustomerEmail());
            customerPhone = trimToNull(request.getCustomerPhone());
        }

        // --- VALIDATION LOGIC GOM LỖI ---

        // 5.1 Validate Address (Bắt buộc cho cả User và Guest trong mọi trường hợp)
        if (shippingAddress == null || shippingAddress.length() < 3) {
            errors.put("shippingAddress", "Shipping address is required (min 3 chars)");
        }

        // 5.2 Validate thông tin cá nhân cho Guest
        if (!isLoggedIn) {
            // Guest Transfer: Cần FullName, Email, Phone
            // (Guest COD đã bị chặn ở trên rồi)
            if ("BANK_TRANSFER".equals(paymentType)) {
                if (customerFullName == null) {
                    errors.put("customerFullName", "Full name is required");
                }
                if (customerEmail == null) {
                    errors.put("customerEmail", "Email is required");
                }
                if (customerPhone == null) {
                    errors.put("customerPhone", "Phone number is required");
                }
            }
        } else {
            // User Logged In:
            // Đã có Address check ở 5.1
            // Có thể check thêm Phone nếu muốn chặt chẽ (tùy chọn)
            if (customerPhone == null) {
                // errors.put("customerPhone", "Phone number is required"); // Uncomment nếu muốn bắt buộc
            }
        }

        // 6. NẾU CÓ LỖI -> NÉM EXCEPTION CHỨA MAP LỖI
        if (!errors.isEmpty()) {
            throw new CheckoutValidationException(errors);
        }

        // =========================================================
        // KHÔNG CÓ LỖI -> TIẾN HÀNH TẠO ĐƠN
        // =========================================================

        BigDecimal subTotal = cartView.getTotals().getSubTotalBeforeTax();
        BigDecimal totalAfterTax = cartView.getTotals().getTotalAfterTax();
        BigDecimal taxTotal = totalAfterTax.subtract(subTotal).max(BigDecimal.ZERO);

        ProductInvoice invoice = new ProductInvoice();
        invoice.setInvoiceCode(cartView.getInvoiceCode());
        invoice.setSubTotal(subTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxTotal(taxTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(totalAfterTax.setScale(2, RoundingMode.HALF_UP));
        invoice.setCurrency(currency);

        invoice.setPaymentMethod(paymentType);
        invoice.setPaymentChannel(paymentChannel);
        invoice.setPaymentStatus("PENDING");

        invoice.setCustomerFullName(customerFullName);
        invoice.setCustomerEmail(customerEmail);
        invoice.setCustomerPhone(customerPhone);
        invoice.setShippingAddress(shippingAddress);
        invoice.setNotes(note);

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
            item.setNote(null);

            if (product.getUnit() != null) {
                int remaining = product.getUnit() - cartItem.getQuantity();
                if (remaining < 0) {
                    throw new IllegalStateException("Not enough stock for product: " + product.getProductName());
                }
                item.setRemainingQuantityAfterSale(remaining);

                // Trừ kho
                product.setUnit(remaining);
                productRepository.save(product);
            } else {
                item.setRemainingQuantityAfterSale(null);
            }

            invoiceItems.add(item);
        }

        productInvoiceItemRepository.saveAll(invoiceItems);

        // Xóa giỏ hàng sau khi thành công
        cartService.clearCart(session);

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
        dto.setPaymentMethod(invoice.getPaymentMethod());
        dto.setPaymentChannel(invoice.getPaymentChannel());
        dto.setPaymentCompletedAt(invoice.getPaymentCompletedAt());
        dto.setInvoiceDate(invoice.getInvoiceDate());
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
}