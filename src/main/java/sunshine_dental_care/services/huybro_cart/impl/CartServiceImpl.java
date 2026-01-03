package sunshine_dental_care.services.huybro_cart.impl;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import sunshine_dental_care.dto.huybro_cart.AddToCartRequestDto;
import sunshine_dental_care.dto.huybro_cart.CartItemDto;
import sunshine_dental_care.dto.huybro_cart.CartTotalsDto;
import sunshine_dental_care.dto.huybro_cart.CartViewDto;
import sunshine_dental_care.dto.huybro_cart.UpdateCartItemRequestDto;
import sunshine_dental_care.entities.huybro_products.Product;
import sunshine_dental_care.entities.huybro_products.ProductImage;
import sunshine_dental_care.repositories.huybro_products.ProductImageRepository;
import sunshine_dental_care.repositories.huybro_products.ProductInventoryRepository; // [IMPORT MỚI]
import sunshine_dental_care.repositories.huybro_products.ProductRepository;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.huybro_cart.interfaces.CartService;
import sunshine_dental_care.utils.huybro_utils.format.FormatCurrencyCart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private static final String CART_SESSION_KEY = "SUNSHINE_CART_ITEMS";
    private static final String CART_INVOICE_CODE_KEY = "SUNSHINE_INVOICE_CODE";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductInventoryRepository productInventoryRepository; // [FIELD MỚI]
    private final FormatCurrencyCart formatCurrencyCart;
    private final UserCartStoreService userCartStore;

    // Constructor Injection
    public CartServiceImpl(ProductRepository productRepository,
                           ProductImageRepository productImageRepository,
                           ProductInventoryRepository productInventoryRepository, // [INJECT]
                           FormatCurrencyCart formatCurrencyCart,
                           UserCartStoreService userCartStore) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.formatCurrencyCart = formatCurrencyCart;
        this.userCartStore = userCartStore;
    }

    private String resolveInvoiceCode(HttpSession session) {
        Integer userId = getCurrentUserIdOrNull();
        if (userId != null) {
            String code = userCartStore.getInvoiceCode(userId);
            if (code == null) {
                code = generateInvoiceCode();
                userCartStore.setInvoiceCode(userId, code);
            }
            return code;
        } else {
            String code = (String) session.getAttribute(CART_INVOICE_CODE_KEY);
            if (code == null) {
                code = generateInvoiceCode();
                session.setAttribute(CART_INVOICE_CODE_KEY, code);
            }
            return code;
        }
    }

    @Override
    public CartViewDto getCartDetail(HttpSession session) {
        Map<Integer, CartItemDto> items = getCartMap(session);
        String invoiceCode = resolveInvoiceCode(session);
        return buildCartView(items, invoiceCode);
    }

    @Override
    @Transactional
    public CartViewDto createCartItem(AddToCartRequestDto request, HttpSession session) {
        Map<Integer, CartItemDto> items = getCartMap(session);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Product is inactive");
        }

        int currentQty = items.containsKey(product.getId())
                ? items.get(product.getId()).getQuantity()
                : 0;
        int newQty = currentQty + request.getQuantity();

        // [LOGIC MỚI]: Check tổng tồn kho (Q1 + Q9) thay vì check product.getUnit()
        checkGlobalStock(product.getId(), newQty);

        CartItemDto item = mapProductToCartItem(product, newQty);
        items.put(product.getId(), item);
        session.setAttribute(CART_SESSION_KEY, items);

        String invoiceCode = resolveInvoiceCode(session);
        return buildCartView(items, invoiceCode);
    }

    @Override
    @Transactional
    public CartViewDto updateCartItemQuantity(UpdateCartItemRequestDto request, HttpSession session) {
        Map<Integer, CartItemDto> items = getCartMap(session);
        String invoiceCode = resolveInvoiceCode(session);

        if (!items.containsKey(request.getProductId())) {
            throw new IllegalArgumentException("Cart item not found");
        }

        if (request.getQuantity() == 0) {
            items.remove(request.getProductId());
            session.setAttribute(CART_SESSION_KEY, items);
            return buildCartView(items, invoiceCode);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // [LOGIC MỚI]: Check tổng tồn kho
        checkGlobalStock(request.getProductId(), request.getQuantity());

        CartItemDto item = mapProductToCartItem(product, request.getQuantity());
        items.put(product.getId(), item);
        session.setAttribute(CART_SESSION_KEY, items);

        return buildCartView(items, invoiceCode);
    }

    @Override
    @Transactional
    public CartViewDto removeCartItem(Integer productId, HttpSession session) {
        Map<Integer, CartItemDto> items = getCartMap(session);
        items.remove(productId);
        session.setAttribute(CART_SESSION_KEY, items);
        String invoiceCode = resolveInvoiceCode(session);
        return buildCartView(items, invoiceCode);
    }

    @Override
    @Transactional
    public void clearCart(HttpSession session) {
        Integer userId = getCurrentUserIdOrNull();
        if (userId != null) {
            userCartStore.clearCart(userId);
        } else {
            session.removeAttribute(CART_SESSION_KEY);
            session.removeAttribute(CART_INVOICE_CODE_KEY);
        }
    }

    @Override
    public CartViewDto getCartPreviewByCurrency(HttpSession session, String targetCurrency) {
        Map<Integer, CartItemDto> items = getCartMap(session);
        String invoiceCode = resolveInvoiceCode(session);

        String normalizedTarget = (targetCurrency == null || targetCurrency.isBlank())
                ? "USD" : targetCurrency.toUpperCase();

        if (items.isEmpty()) {
            CartTotalsDto totals = new CartTotalsDto();
            totals.setSubTotalBeforeTax(BigDecimal.ZERO);
            totals.setTotalAfterTax(BigDecimal.ZERO);
            CartViewDto view = new CartViewDto();
            view.setItems(List.of());
            view.setTotals(totals);
            view.setInvoiceCode(invoiceCode);
            view.setCurrency(normalizedTarget);
            if ("VND".equals(normalizedTarget)) {
                view.setExchangeRateToVnd(formatCurrencyCart.getRate("USD", "VND"));
            }
            return view;
        }

        BigDecimal usdToVndRate = null;
        if ("VND".equals(normalizedTarget)) {
            usdToVndRate = formatCurrencyCart.getRate("USD", "VND");
        }
        BigDecimal finalUsdToVndRate = usdToVndRate;

        List<CartItemDto> convertedItems = items.values().stream()
                .map(item -> convertItemCurrency(item, normalizedTarget, finalUsdToVndRate))
                .toList();

        CartTotalsDto totals = new CartTotalsDto();
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal totalAfterTax = BigDecimal.ZERO;

        for (CartItemDto item : convertedItems) {
            BigDecimal lineBeforeTax = item.getUnitPriceBeforeTax().multiply(BigDecimal.valueOf(item.getQuantity()));
            subTotal = subTotal.add(lineBeforeTax);
            totalAfterTax = totalAfterTax.add(item.getLineTotalAmount());
        }

        totals.setSubTotalBeforeTax(subTotal);
        totals.setTotalAfterTax(totalAfterTax);

        CartViewDto view = new CartViewDto();
        view.setItems(convertedItems);
        view.setTotals(totals);
        view.setInvoiceCode(invoiceCode);
        view.setCurrency(normalizedTarget);
        if ("VND".equals(normalizedTarget)) {
            view.setExchangeRateToVnd(usdToVndRate);
        }
        return view;
    }

    // --- PRIVATE HELPERS ---

    // [HELPER MỚI]: Check tổng tồn kho (Q1 + Q9)
    private void checkGlobalStock(Integer productId, int requestedQty) {
        Integer totalAvailable = productInventoryRepository.sumTotalQuantityByProductId(productId);
        if (totalAvailable == null) totalAvailable = 0;

        if (totalAvailable < requestedQty) {
            throw new IllegalArgumentException("Sorry, we only have " + totalAvailable + " items in stock (Total).");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, CartItemDto> getCartMap(HttpSession session) {
        Integer userId = getCurrentUserIdOrNull();
        if (userId != null) {
            return userCartStore.getOrCreateCart(userId);
        }
        Object attr = session.getAttribute(CART_SESSION_KEY);
        if (attr instanceof Map<?, ?> map) {
            return (Map<Integer, CartItemDto>) map;
        }
        Map<Integer, CartItemDto> newMap = new LinkedHashMap<>();
        session.setAttribute(CART_SESSION_KEY, newMap);
        return newMap;
    }

    private CartItemDto mapProductToCartItem(Product product, int quantity) {
        BigDecimal unitPriceBeforeTax = product.getDefaultRetailPrice();
        if (unitPriceBeforeTax == null) throw new IllegalStateException("Product price is not set");

        BigDecimal taxRatePercent = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(product.getIsTaxable()) && product.getTaxCode() != null && product.getTaxCode().compareTo(BigDecimal.ZERO) > 0) {
            taxRatePercent = product.getTaxCode();
        }

        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal lineSubTotalBeforeTax = unitPriceBeforeTax.multiply(qty);

        BigDecimal taxAmount = lineSubTotalBeforeTax.multiply(taxRatePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal lineTotalAfterTax = lineSubTotalBeforeTax.add(taxAmount);

        BigDecimal unitTaxAmount = unitPriceBeforeTax.multiply(taxRatePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal unitPriceAfterTax = unitPriceBeforeTax.add(unitTaxAmount);

        CartItemDto dto = new CartItemDto();
        dto.setProductId(product.getId());
        dto.setSku(product.getSku());
        dto.setProductName(product.getProductName());
        dto.setBrand(product.getBrand());
        dto.setQuantity(quantity);
        dto.setUnitPriceBeforeTax(unitPriceBeforeTax);
        dto.setTaxRatePercent(taxRatePercent);
        dto.setTaxAmount(taxAmount);
        dto.setUnitPriceAfterTax(unitPriceAfterTax);
        dto.setLineTotalAmount(lineTotalAfterTax);
        dto.setCurrency(product.getCurrency());
        dto.setMainImageUrl(resolveMainImageUrl(product.getId()));
        return dto;
    }

    private String resolveMainImageUrl(Integer productId) {
        List<ProductImage> images = productImageRepository.findByProduct_IdOrderByImageOrderAsc(productId);
        if (CollectionUtils.isEmpty(images)) return null;
        String rawPath = images.get(0).getImageUrl();
        if (rawPath == null || rawPath.isBlank()) return null;
        String fileName = Paths.get(rawPath).getFileName().toString();
        return "/api/products/images/" + fileName;
    }

    private CartViewDto buildCartView(Map<Integer, CartItemDto> items, String invoiceCode) {
        CartTotalsDto totals = new CartTotalsDto();
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal totalAfterTax = BigDecimal.ZERO;
        for (CartItemDto item : items.values()) {
            BigDecimal lineBeforeTax = item.getUnitPriceBeforeTax().multiply(BigDecimal.valueOf(item.getQuantity()));
            subTotal = subTotal.add(lineBeforeTax);
            totalAfterTax = totalAfterTax.add(item.getLineTotalAmount());
        }
        totals.setSubTotalBeforeTax(subTotal);
        totals.setTotalAfterTax(totalAfterTax);
        CartViewDto view = new CartViewDto();
        view.setItems(List.copyOf(items.values()));
        view.setTotals(totals);
        view.setInvoiceCode(invoiceCode);
        return view;
    }

    private String generateInvoiceCode() {
        String datetimePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss-ddMMyyyy"));
        String randomPart = RandomStringUtils.randomAlphanumeric(6).toUpperCase();
        return "INV-" + datetimePart + "-" + randomPart;
    }

    private CartItemDto convertItemCurrency(CartItemDto item, String targetCurrency, BigDecimal usdToVndRate) {
        String fromCurrency = item.getCurrency();
        if (fromCurrency == null || fromCurrency.equalsIgnoreCase(targetCurrency)) return item;
        BigDecimal rateToUse;
        if ("USD".equalsIgnoreCase(fromCurrency) && "VND".equalsIgnoreCase(targetCurrency)) rateToUse = usdToVndRate;
        else rateToUse = formatCurrencyCart.getRate(fromCurrency, targetCurrency);

        CartItemDto converted = new CartItemDto();
        converted.setProductId(item.getProductId());
        converted.setSku(item.getSku());
        converted.setProductName(item.getProductName());
        converted.setBrand(item.getBrand());
        converted.setMainImageUrl(item.getMainImageUrl());
        converted.setQuantity(item.getQuantity());
        converted.setTaxRatePercent(item.getTaxRatePercent());
        converted.setUnitPriceBeforeTax(formatCurrencyCart.convert(item.getUnitPriceBeforeTax(), rateToUse, fromCurrency, targetCurrency));
        converted.setTaxAmount(formatCurrencyCart.convert(item.getTaxAmount(), rateToUse, fromCurrency, targetCurrency));
        converted.setUnitPriceAfterTax(formatCurrencyCart.convert(item.getUnitPriceAfterTax(), rateToUse, fromCurrency, targetCurrency));
        converted.setLineTotalAmount(formatCurrencyCart.convert(item.getLineTotalAmount(), rateToUse, fromCurrency, targetCurrency));
        converted.setCurrency(targetCurrency);
        return converted;
    }

    private Integer getCurrentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof CurrentUser currentUser) {
            return currentUser.userId();
        }
        return null;
    }
}