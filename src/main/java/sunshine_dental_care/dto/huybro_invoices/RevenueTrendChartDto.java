package sunshine_dental_care.dto.huybro_invoices;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.temporal.Temporal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RevenueTrendChartDto {

    // Điểm thời gian tổng hợp (ví dụ: LocalDate cho ngày, YearMonth cho tháng).
    // Được định nghĩa là Temporal để hỗ trợ các độ phân giải thời gian khác nhau (Ngày, Tháng, Năm).
    private Temporal timePoint;

    // Tổng doanh thu cộng dồn cho điểm thời gian cụ thể này.
    private BigDecimal totalRevenue;

    // Tổng thuế cộng dồn cho điểm thời gian cụ thể này.
    private BigDecimal totalTax;
}