package sunshine_dental_care.dto.huybro_inventories;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductStockReceiptCreateDto {

    @NotNull(message = "Product ID is required")
    private Integer productId;

    @NotNull(message = "Clinic ID is required")
    private Integer clinicId;

    @NotNull(message = "Quantity added is required")
    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantityAdded;

    @NotNull(message = "Import price is required")
    @Min(value = 0, message = "Import price cannot be negative")
    private BigDecimal importPrice;

    // Bắt buộc nhập % lợi nhuận (nếu không nhập thì mặc định 0 -> bán hòa vốn)
    @Min(value = 0, message = "Profit margin cannot be negative")
    private BigDecimal profitMargin;

    // [ĐÃ XÓA] newRetailPrice -> Hệ thống tự tính, không cho nhập

    @Size(max = 3, message = "Currency code max 3 characters (e.g. USD, VND)")
    private String currency;

    @Size(max = 500, message = "Note cannot exceed 500 characters")
    private String note;
}