package sunshine_dental_care.dto.adminDTO;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportDto {
    private BigDecimal netRevenue;
    private BigDecimal potentialRevenue;
    private BigDecimal lostRevenue;
    private Long totalOrdersCompleted;
    private Long totalOrdersCancelled;
    private List<ChartDataDto> chartData;
    private List<TopProductDto> topProducts;
}
