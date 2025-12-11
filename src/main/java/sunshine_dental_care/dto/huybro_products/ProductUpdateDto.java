package sunshine_dental_care.dto.huybro_products;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductUpdateDto {

    @NotBlank(message = "SKU must not be empty")
    @Size(min = 3, max = 64, message = "SKU must be between 5 and 64 characters")
    private String sku;

    @NotBlank(message = "Product name must not be empty")
    @Size(min = 3, max = 200, message = "Product name must be between 5 and 200 characters")
    private String productName;

    @NotBlank(message = "Brand must not be empty")
    @Size(min = 1, max = 100, message = "Brand must be between 1 and 100 characters")
    private String brand;

    @NotBlank(message = "Product description must not be empty")
    @Size(min = 10, max = 1000, message = "Product description must be between 10 and 1000 characters")
    private String productDescription;

    @NotNull(message = "Unit must not be null")
    @Positive(message = "Unit must be greater than 0")
    private Integer unit;

    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal defaultRetailPrice;

    @NotBlank(message = "Currency must not be empty")
    @Pattern(regexp = "USD|VND", message = "Currency must be USD or VND")
    private String currency = "USD";

    private Boolean isTaxable = true;

    @DecimalMin(value = "0.0", message = "Tax code must be at least 0")
    @DecimalMax(value = "99.0", message = "Tax code must be at most 99")
    private BigDecimal taxCode;

    private Boolean isActive = true;

    @NotNull(message = "Product images must not be null")
    @Size(min = 3, max = 3, message = "Product must contain exactly 3 images")
    @Valid
    private List<ProductImageUpdateDto> image;

    @NotNull(message = "Product type must not be null")
    @Size(min = 1, message = "Product must have at least 1 type")
    private List<@NotBlank(message = "Type name must not be empty") String> typeNames;
}
