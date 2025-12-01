package sunshine_dental_care.services.huybro_cart.impl;

import org.springframework.stereotype.Component;
import sunshine_dental_care.dto.huybro_cart.CartItemDto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserCartStoreService {
    // userId -> (productId -> CartItemDto)
    private final Map<Integer, Map<Integer, CartItemDto>> carts = new ConcurrentHashMap<>();

    // userId -> invoiceCode (Lưu mã hóa đơn cố định cho user)
    private final Map<Integer, String> invoiceCodes = new ConcurrentHashMap<>();

    public Map<Integer, CartItemDto> getOrCreateCart(Integer userId) {
        return carts.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
    }

    public String getInvoiceCode(Integer userId) {
        return invoiceCodes.get(userId);
    }

    public void setInvoiceCode(Integer userId, String code) {
        invoiceCodes.put(userId, code);
    }

    public void clearCart(Integer userId) {
        carts.remove(userId);
        invoiceCodes.remove(userId); // Xóa mã hóa đơn khi clear cart
    }
}