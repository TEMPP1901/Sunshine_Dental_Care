package sunshine_dental_care.dto.adminDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa tất cả thống kê cho Admin Dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatisticsDto {

    // Revenue Statistics
    private BigDecimal todayRevenue;
    private BigDecimal weekRevenue;
    private BigDecimal monthRevenue;
    private BigDecimal previousMonthRevenue;
    private BigDecimal totalExpenses; // tổng chi phí (0 nếu chưa có module)
    private Boolean expensesSupported; // false => chưa có module chi phí
    private BigDecimal netProfit; // monthRevenue - totalExpenses
    private Double monthOverMonthGrowth; // %
    private List<DailyRevenueDto> last7DaysRevenue; // Cho biểu đồ

    // Appointments Statistics
    private Long todayAppointments;
    private Long weekAppointments;
    private Long monthAppointments;
    private Map<String, Long> appointmentsByStatus; // CONFIRMED, COMPLETED, CANCELLED, etc.

    // Patients Statistics
    private Long totalPatients;
    private Long todayNewPatients;
    private Long weekNewPatients;
    private Long monthNewPatients;

    // Other Metrics (đã có sẵn từ các API khác)
    private Long totalStaff;
    private Long totalClinics;
    private Long activeClinics;
    private Long todayAttendance;
    private Long pendingLeaveRequests;

    // Customer & retention
    private Double retentionRate; // %
    private Long returningPatients;
    private Long patientsThisMonth;

    // HR performance
    private List<TopDoctorPerformanceDto> topDoctors;

    // Alerts & marketing
    private Long todayCancelledAppointments;
    private Map<String, Long> sourceBreakdown; // channel -> count (monthly)
}
