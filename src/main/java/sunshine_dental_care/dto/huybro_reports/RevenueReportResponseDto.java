package sunshine_dental_care.dto.huybro_reports;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportResponseDto {
    // 1. Các chỉ số tổng quan (KPI Cards)
    private BigDecimal netRevenue;          // Doanh thu thực (COMPLETED)
    private BigDecimal potentialRevenue;    // Doanh thu tiềm năng (CONFIRMED + PROCESSING)
    private BigDecimal lostRevenue;         // Doanh thu mất (CANCELLED / RETURNED)
    private Long totalOrdersCompleted;      // Tổng đơn thành công
    private Long totalOrdersCancelled;      // Tổng đơn hủy

    // 2. Dữ liệu biểu đồ (Line Chart)
    private List<RevenueChartDataDto> chartData;

    // 3. Top sản phẩm (Table)
    private List<TopSellingProductDto> topProducts;
}