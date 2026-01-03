package sunshine_dental_care.api.huybro_cart;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_cart.AddToCartRequestDto;
import sunshine_dental_care.dto.huybro_cart.CartViewDto;
import sunshine_dental_care.dto.huybro_cart.UpdateCartItemRequestDto;
import sunshine_dental_care.services.huybro_cart.interfaces.CartService;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartViewDto> getCartDetail(HttpSession session) {
        CartViewDto view = cartService.getCartDetail(session);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/items")
    public ResponseEntity<CartViewDto> createCartItem(
            @Valid @RequestBody AddToCartRequestDto request,
            HttpSession session
    ) {
        CartViewDto view = cartService.createCartItem(request, session);
        return ResponseEntity.ok(view);
    }

    @PutMapping("/items")
    public ResponseEntity<CartViewDto> updateCartItemQuantity(
            @Valid @RequestBody UpdateCartItemRequestDto request,
            HttpSession session
    ) {
        CartViewDto view = cartService.updateCartItemQuantity(request, session);
        return ResponseEntity.ok(view);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartViewDto> removeCartItem(
            @PathVariable Integer productId,
            HttpSession session
    ) {
        CartViewDto view = cartService.removeCartItem(productId, session);
        return ResponseEntity.ok(view);
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(HttpSession session) {
        cartService.clearCart(session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/checkout-preview")
    public ResponseEntity<CartViewDto> getCheckoutPreview(
            @RequestParam(name = "currency") String currency,
            HttpSession session
    ) {
        CartViewDto view = cartService.getCartPreviewByCurrency(session, currency);
        return ResponseEntity.ok(view);
    }

}
