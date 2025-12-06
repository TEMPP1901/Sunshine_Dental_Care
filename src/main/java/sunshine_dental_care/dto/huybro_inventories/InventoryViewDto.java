package sunshine_dental_care.dto.huybro_inventories;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryViewDto {
    private Integer inventoryId;
    private Integer productId;
    private String sku;
    private String productName;
    private String image; // URL ảnh
    private Integer clinicId;
    private String clinicName;
    private Integer quantity; // Số lượng tại kho này

    // Giá bán & Tiền tệ (Lấy từ Product gốc)
    private BigDecimal currentRetailPrice;
    private String currency;

    private Integer minStockLevel;
    private LocalDateTime lastUpdated;
}