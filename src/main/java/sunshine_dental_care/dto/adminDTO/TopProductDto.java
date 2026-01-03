package sunshine_dental_care.dto.adminDTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductDto {
    private String productName;
    private String sku;
    private Long totalSoldQty;
    private BigDecimal totalRevenue;
}
