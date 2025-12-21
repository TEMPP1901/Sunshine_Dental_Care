package sunshine_dental_care.dto.huybro_inventories;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductStockReceiptDto {
    private Integer receiptId;
    private Integer productId;
    private String productName;
    private Integer clinicId;
    private String clinicName;
    private Integer quantityAdded;
    private BigDecimal importPrice;
    private BigDecimal newRetailPrice;
    private String note;
    private LocalDateTime createdAt;
    private BigDecimal profitMargin;
    private String currency;
}