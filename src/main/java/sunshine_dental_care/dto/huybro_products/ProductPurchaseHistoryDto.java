package sunshine_dental_care.dto.huybro_products;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductPurchaseHistoryDto {
    private String customerName; // Tên thật (VD: Nguyen Van A)
    private BigDecimal price;    // Giá họ đã mua
    private Integer quantity;    // Số lượng họ mua
    private LocalDateTime purchaseDate;
}