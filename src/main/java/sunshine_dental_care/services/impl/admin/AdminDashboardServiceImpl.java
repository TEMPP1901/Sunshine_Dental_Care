package sunshine_dental_care.services.impl.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.DailyRevenueDto;
import sunshine_dental_care.dto.adminDTO.DashboardStatisticsDto;
import sunshine_dental_care.dto.adminDTO.TopDoctorPerformanceDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.repositories.admin.AdminAppointmentStatsRepository;
import sunshine_dental_care.repositories.admin.AdminInvoiceStatsRepository;
import sunshine_dental_care.repositories.admin.AdminPatientStatsRepository;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.services.interfaces.admin.AdminDashboardService;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

        private final UserRepo userRepo;
        private final ClinicRepo clinicRepo;
        private final LeaveRequestRepo leaveRequestRepo;
        private final AttendanceService attendanceService;
        private final AdminInvoiceStatsRepository adminInvoiceStatsRepository;
        private final AdminAppointmentStatsRepository adminAppointmentStatsRepository;
        private final AdminPatientStatsRepository adminPatientStatsRepository;

        @Override
        @Transactional(readOnly = true)
        public DashboardStatisticsDto getDashboardStatistics() {
                try {
                        LocalDate today = LocalDate.now();
                        LocalDate weekStart = today.minus(6, ChronoUnit.DAYS);
                        LocalDate monthStart = today.withDayOfMonth(1);
                        LocalDate prevMonthStart = monthStart.minusMonths(1);
                        LocalDate prevMonthEnd = monthStart.minusDays(1);
                        Instant dayStart = toStartOfDay(today);
                        Instant dayEnd = toStartOfDay(today.plusDays(1));
                        Instant weekRangeStart = toStartOfDay(weekStart);
                        Instant weekEnd = dayEnd;
                        Instant monthRangeStart = toStartOfDay(monthStart);
                        Instant monthRangeEnd = dayEnd;

                        // Thống kê doanh số (tổng giá trị hóa đơn)
                        BigDecimal todayTotalSales = BigDecimal.ZERO;
                        BigDecimal weekTotalSales = BigDecimal.ZERO;
                        BigDecimal monthTotalSales = BigDecimal.ZERO;
                        BigDecimal prevMonthTotalSales = BigDecimal.ZERO;
                        
                        // Thống kê tiền thực thu (chỉ đã thanh toán)
                        BigDecimal todayRevenue = BigDecimal.ZERO;
                        BigDecimal weekRevenue = BigDecimal.ZERO;
                        BigDecimal monthRevenue = BigDecimal.ZERO;
                        BigDecimal prevMonthRevenue = BigDecimal.ZERO;
                        
                        BigDecimal totalExpenses = BigDecimal.ZERO; // Placeholder cho tổng chi phí (sẽ thay khi có nguồn dữ liệu thật)
                        boolean expensesSupported = false;
                        BigDecimal netProfit = BigDecimal.ZERO;
                        Double monthOverMonthGrowth = 0.0;
                        List<DailyRevenueDto> last7DaysRevenue = new ArrayList<>();
                        try {
                                // Tính doanh số (tất cả hóa đơn)
                                todayTotalSales = adminInvoiceStatsRepository.sumTotalSalesBetween(today, today);
                                weekTotalSales = adminInvoiceStatsRepository.sumTotalSalesBetween(weekStart, today);
                                monthTotalSales = adminInvoiceStatsRepository.sumTotalSalesBetween(monthStart, today);
                                prevMonthTotalSales = adminInvoiceStatsRepository.sumTotalSalesBetween(prevMonthStart, prevMonthEnd);
                                
                                // Tính tiền thực thu (chỉ đã thanh toán)
                                todayRevenue = adminInvoiceStatsRepository.sumRevenueBetween(today, today);
                                weekRevenue = adminInvoiceStatsRepository.sumRevenueBetween(weekStart, today);
                                monthRevenue = adminInvoiceStatsRepository.sumRevenueBetween(monthStart, today);
                                prevMonthRevenue = adminInvoiceStatsRepository.sumRevenueBetween(prevMonthStart, prevMonthEnd);
                                
                                totalExpenses = calculateExpenses(monthStart, today);
                                expensesSupported = totalExpenses != null && totalExpenses.compareTo(BigDecimal.ZERO) >= 0;
                                netProfit = monthRevenue.subtract(totalExpenses);

                                // Tính % tăng trưởng doanh thu so với tháng trước
                                if (prevMonthRevenue.compareTo(BigDecimal.ZERO) > 0) {
                                        monthOverMonthGrowth = monthRevenue.subtract(prevMonthRevenue)
                                                        .divide(prevMonthRevenue, 4, java.math.RoundingMode.HALF_UP)
                                                        .multiply(BigDecimal.valueOf(100))
                                                        .doubleValue();
                                } else if (monthRevenue.compareTo(BigDecimal.ZERO) > 0) {
                                        monthOverMonthGrowth = 100.0;
                                } else {
                                        monthOverMonthGrowth = 0.0;
                                }
                                // Danh sách doanh thu từng ngày trong 7 ngày gần nhất
                                last7DaysRevenue = buildDailyRevenue(weekStart, today);
                        } catch (Exception e) {
                                log.error("Lỗi khi tính doanh thu: {}", e.getMessage(), e);
                        }

                        // Thống kê lịch hẹn
                        Long todayAppointments = 0L;
                        Long weekAppointments = 0L;
                        Long monthAppointments = 0L;
                        Long todayCancelledAppointments = 0L;
                        Map<String, Long> appointmentsByStatus = new HashMap<>();
                        try {
                                List<String> validStatuses = List.of("CONFIRMED", "COMPLETED", "SCHEDULED");
                                todayAppointments = adminAppointmentStatsRepository.countByStartBetweenAndStatusIn(
                                                dayStart, dayEnd, validStatuses);
                                weekAppointments = adminAppointmentStatsRepository.countByStartBetweenAndStatusIn(
                                                weekRangeStart, weekEnd, validStatuses);
                                monthAppointments = adminAppointmentStatsRepository.countByStartBetweenAndStatusIn(
                                                monthRangeStart, weekEnd, validStatuses);
                                todayCancelledAppointments = adminAppointmentStatsRepository.countCancelledBetween(dayStart, dayEnd);

                                // Nhóm số lượng lịch theo trạng thái trong tháng
                                appointmentsByStatus = adminAppointmentStatsRepository.countByStatusBetween(
                                                monthRangeStart, weekEnd).stream()
                                                .collect(Collectors.toMap(
                                                                AdminAppointmentStatsRepository.StatusCountView::getStatus,
                                                                AdminAppointmentStatsRepository.StatusCountView::getTotal));
                        } catch (Exception e) {
                                log.error("Lỗi khi đếm số lịch hẹn: {}", e.getMessage(), e);
                        }

                        // Thống kê khách hàng
                        Long totalPatients = 0L;
                        Long todayNewPatients = 0L;
                        Long weekNewPatients = 0L;
                        Long monthNewPatients = 0L;
                        try {
                                totalPatients = adminPatientStatsRepository.countAllPatients();
                                todayNewPatients = adminPatientStatsRepository.countCreatedBetween(dayStart, dayEnd);
                                weekNewPatients = adminPatientStatsRepository.countCreatedBetween(weekRangeStart, weekEnd);
                                monthNewPatients = adminPatientStatsRepository.countCreatedBetween(monthRangeStart, weekEnd);
                        } catch (Exception e) {
                                log.error("Lỗi khi đếm bệnh nhân: {}", e.getMessage(), e);
                        }

                        // Thống kê nhân viên, phòng khám
                        Long totalStaff = 0L;
                        Long totalClinics = 0L;
                        Long activeClinics = 0L;
                        try {
                                totalStaff = userRepo.count();
                                List<Clinic> allClinics = clinicRepo.findAll();
                                totalClinics = (long) allClinics.size();
                                activeClinics = allClinics.stream()
                                                .filter(c -> c.getIsActive() != null && c.getIsActive())
                                                .count();
                        } catch (Exception e) {
                                log.error("Lỗi khi đếm nhân viên/phòng khám: {}", e.getMessage(), e);
                        }

                        // Lấy số bản ghi chấm công hôm nay
                        Long todayAttendance = 0L;
                        try {
                                todayAttendance = (long) attendanceService.getAttendanceForAdmin(today, null, null).size();
                        } catch (Exception e) {
                                log.warn("Lỗi khi lấy số chấm công hôm nay: {}", e.getMessage());
                        }

                        // Số đơn xin nghỉ phép đang chờ xử lý
                        Long pendingLeaveRequests = 0L;
                        try {
                                pendingLeaveRequests = leaveRequestRepo.countByStatus("PENDING");
                        } catch (Exception e) {
                                log.warn("Lỗi khi lấy số đơn nghỉ phép: {}", e.getMessage());
                        }

                        // Tỷ lệ quay lại khám/thống kê nguồn khách của bệnh nhân trong tháng
                        double retentionRate = 0.0;
                        long returningPatients = 0L;
                        long patientsThisMonth = 0L;
                        Map<String, Long> sourceBreakdown = new HashMap<>();
                        try {
                                List<Integer> patientsInRange = adminAppointmentStatsRepository
                                                .findDistinctPatientIdsInRange(monthRangeStart, monthRangeEnd);
                                List<Integer> patientsBefore = adminAppointmentStatsRepository
                                                .findDistinctPatientIdsBefore(monthRangeStart);

                                patientsThisMonth = patientsInRange.size();
                                returningPatients = patientsInRange.stream()
                                                .filter(patientsBefore::contains)
                                                .count();

                                retentionRate = patientsThisMonth > 0
                                                ? (returningPatients * 100.0) / patientsThisMonth
                                                : 0.0;

                                // Phân bổ theo kênh/phương thức tiếp cận khách hàng trong tháng
                                sourceBreakdown = adminAppointmentStatsRepository.countByChannelBetween(
                                                monthRangeStart, monthRangeEnd).stream()
                                                .collect(Collectors.toMap(
                                                                view -> view.getChannel() != null ? view.getChannel() : "UNKNOWN",
                                                                AdminAppointmentStatsRepository.ChannelCountView::getTotal));
                        } catch (Exception e) {
                                log.warn("Lỗi khi tính tỷ lệ quay lại khám, phân bổ nguồn khách: {}", e.getMessage());
                        }

                        // Top 5 bác sĩ có doanh thu cao nhất tháng này
                        List<TopDoctorPerformanceDto> topDoctors = new ArrayList<>();
                        try {
                                List<AdminInvoiceStatsRepository.DoctorRevenueView> doctorRevenueViews = adminInvoiceStatsRepository
                                                .findDoctorRevenueBetween(monthStart, today);

                                topDoctors = doctorRevenueViews.stream()
                                                .map(view -> TopDoctorPerformanceDto.builder()
                                                                .doctorId(view.getDoctorId())
                                                                .doctorName(view.getDoctorName())
                                                                .revenue(view.getRevenue())
                                                                .completedAppointments(view.getCompletedAppointments())
                                                                .build())
                                                .limit(5)
                                                .collect(Collectors.toList());
                        } catch (Exception e) {
                                log.warn("Lỗi khi tính top bác sĩ: {}", e.getMessage());
                        }

                        return DashboardStatisticsDto.builder()
                                        .todayTotalSales(todayTotalSales)
                                        .weekTotalSales(weekTotalSales)
                                        .monthTotalSales(monthTotalSales)
                                        .previousMonthTotalSales(prevMonthTotalSales)
                                        .todayRevenue(todayRevenue)
                                        .weekRevenue(weekRevenue)
                                        .monthRevenue(monthRevenue)
                                        .previousMonthRevenue(prevMonthRevenue)
                                        .totalExpenses(totalExpenses)
                                        .expensesSupported(expensesSupported)
                                        .netProfit(netProfit)
                                        .monthOverMonthGrowth(monthOverMonthGrowth)
                                        .last7DaysRevenue(last7DaysRevenue)
                                        .todayAppointments(todayAppointments)
                                        .weekAppointments(weekAppointments)
                                        .monthAppointments(monthAppointments)
                                        .todayCancelledAppointments(todayCancelledAppointments)
                                        .appointmentsByStatus(appointmentsByStatus)
                                        .totalPatients(totalPatients)
                                        .todayNewPatients(todayNewPatients)
                                        .weekNewPatients(weekNewPatients)
                                        .monthNewPatients(monthNewPatients)
                                        .totalStaff(totalStaff)
                                        .totalClinics(totalClinics)
                                        .activeClinics(activeClinics)
                                        .todayAttendance(todayAttendance)
                                        .pendingLeaveRequests(pendingLeaveRequests)
                                        .retentionRate(retentionRate)
                                        .returningPatients(returningPatients)
                                        .patientsThisMonth(patientsThisMonth)
                                        .sourceBreakdown(sourceBreakdown)
                                        .topDoctors(topDoctors)
                                        .build();
                } catch (Exception e) {
                        log.error("Lỗi nghiêm trọng trong getDashboardStatistics: {}", e.getMessage(), e);
                        // Trả về DTO với các giá trị mặc định khi có lỗi
                        return DashboardStatisticsDto.builder()
                                        .todayTotalSales(BigDecimal.ZERO)
                                        .weekTotalSales(BigDecimal.ZERO)
                                        .monthTotalSales(BigDecimal.ZERO)
                                        .previousMonthTotalSales(BigDecimal.ZERO)
                                        .todayRevenue(BigDecimal.ZERO)
                                        .weekRevenue(BigDecimal.ZERO)
                                        .monthRevenue(BigDecimal.ZERO)
                                        .previousMonthRevenue(BigDecimal.ZERO)
                                        .totalExpenses(BigDecimal.ZERO)
                                        .expensesSupported(false)
                                        .netProfit(BigDecimal.ZERO)
                                        .monthOverMonthGrowth(0.0)
                                        .last7DaysRevenue(new ArrayList<>())
                                        .todayAppointments(0L)
                                        .weekAppointments(0L)
                                        .monthAppointments(0L)
                                        .todayCancelledAppointments(0L)
                                        .appointmentsByStatus(new HashMap<>())
                                        .totalPatients(0L)
                                        .todayNewPatients(0L)
                                        .weekNewPatients(0L)
                                        .monthNewPatients(0L)
                                        .totalStaff(0L)
                                        .totalClinics(0L)
                                        .activeClinics(0L)
                                        .todayAttendance(0L)
                                        .pendingLeaveRequests(0L)
                                        .retentionRate(0.0)
                                        .returningPatients(0L)
                                        .patientsThisMonth(0L)
                                        .sourceBreakdown(new HashMap<>())
                                        .topDoctors(new ArrayList<>())
                                        .build();
                }
        }

        // Tạo danh sách doanh thu từng ngày theo khoảng thời gian
        private List<DailyRevenueDto> buildDailyRevenue(LocalDate startDate, LocalDate endDate) {
                Map<LocalDate, AdminInvoiceStatsRepository.DailyRevenueView> revenueByDate = adminInvoiceStatsRepository
                                .findDailyRevenueBetween(startDate, endDate).stream()
                                .collect(Collectors.toMap(AdminInvoiceStatsRepository.DailyRevenueView::getInvoiceDate, view -> view));

                List<DailyRevenueDto> result = new ArrayList<>();
                LocalDate current = startDate;
                while (!current.isAfter(endDate)) {
                        AdminInvoiceStatsRepository.DailyRevenueView view = revenueByDate.get(current);
                        BigDecimal revenue = view != null ? view.getRevenue() : BigDecimal.ZERO;
                        Long orderCount = 0L;
                        if (view != null) {
                                Long count = view.getOrderCount();
                                orderCount = count != null ? count : 0L;
                        }

                        result.add(DailyRevenueDto.builder()
                                        .date(current)
                                        .revenue(revenue)
                                        .orderCount(orderCount)
                                        .build());
                        current = current.plusDays(1);
                }
                return result;
        }

        // Chuyển LocalDate -> Instant ở mốc đầu ngày
        private Instant toStartOfDay(LocalDate date) {
                return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        }

        // Hàm giả lập tổng chi phí (sẽ thay khi có số liệu thực tế)
        @SuppressWarnings("unused")
        private BigDecimal calculateExpenses(LocalDate startDate, LocalDate endDate) {
                return BigDecimal.ZERO;
        }
}
