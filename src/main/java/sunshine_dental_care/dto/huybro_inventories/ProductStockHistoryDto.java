package sunshine_dental_care.dto.huybro_inventories;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductStockHistoryDto {
    private Integer receiptId;
    private String clinicName;
    private Integer quantityAdded;
    private BigDecimal importPrice;     // Giá nhập lúc đó
    private BigDecimal profitMargin;    // Margin lúc đó
    private BigDecimal newRetailPrice;  // Giá bán lúc đó
    private String note;
    private String createdBy;           // Người nhập (nếu có, tạm thời để trống hoặc hardcode)
    private LocalDateTime createdAt;
}