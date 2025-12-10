package sunshine_dental_care.dto.huybro_products;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ProductImageUpdateDto {

    @NotBlank(message = "Image URL must not be empty")
    @Pattern(
            regexp = "(?i)^.+\\.(jpg|jpeg|png)$",
            message = "Image must be in JPG, JPEG, or PNG format"
    )
    private String imageUrl;

    @NotNull(message = "Image order must not be null")
    private Integer imageOrder;
}
