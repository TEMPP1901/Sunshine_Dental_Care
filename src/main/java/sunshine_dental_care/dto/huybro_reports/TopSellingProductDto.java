package sunshine_dental_care.dto.huybro_reports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopSellingProductDto {
    private String productName;
    private String sku;
    private Long totalSoldQty;      // Số lượng đã bán (COMPLETED)
    private BigDecimal totalRevenue; // Doanh thu từ sản phẩm này
}