package sunshine_dental_care.services.huybro_cart.interfaces;

import jakarta.servlet.http.HttpSession;
import sunshine_dental_care.dto.huybro_cart.AddToCartRequestDto;
import sunshine_dental_care.dto.huybro_cart.CartViewDto;
import sunshine_dental_care.dto.huybro_cart.UpdateCartItemRequestDto;

public interface CartService {

    CartViewDto getCartDetail(HttpSession session);

    CartViewDto createCartItem(AddToCartRequestDto request, HttpSession session);

    CartViewDto updateCartItemQuantity(UpdateCartItemRequestDto request, HttpSession session);

    CartViewDto removeCartItem(Integer productId, HttpSession session);

    CartViewDto getCartPreviewByCurrency(HttpSession session, String targetCurrency);

    void clearCart(HttpSession session);

}
