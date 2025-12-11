package sunshine_dental_care.dto.adminDTO;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO cho doanh thu theo ngày (dùng cho biểu đồ).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueDto {
    private LocalDate date;
    private BigDecimal revenue;
    private Long orderCount; // Số đơn hàng trong ngày
}
