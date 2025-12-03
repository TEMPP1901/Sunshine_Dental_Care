package sunshine_dental_care.dto.huybro_cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AddToCartRequestDto {

    @NotNull(message = "Product id must not be null")
    private Integer productId;

    @NotNull(message = "Quantity must not be null")
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;
}
