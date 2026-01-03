package sunshine_dental_care.dto.huybro_reports;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevenueChartDataDto {
    private LocalDate date;
    private BigDecimal revenue; // Chỉ tính COMPLETED
    private Long orderCount;
}