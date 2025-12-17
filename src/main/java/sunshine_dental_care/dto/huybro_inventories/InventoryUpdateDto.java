package sunshine_dental_care.dto.huybro_inventories;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class InventoryUpdateDto {
    @NotNull(message = "Inventory ID is required")
    private Integer inventoryId;

    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer newQuantity;

    @Min(value = 0, message = "Price cannot be negative")
    private BigDecimal newRetailPrice;

    @Min(value = 0)
    private BigDecimal newImportPrice;

    @Min(value = 0)
    private BigDecimal newProfitMargin;

    @Size(max = 3, message = "Currency cannot be negative")
    private String newCurrency;

    private String note;
}