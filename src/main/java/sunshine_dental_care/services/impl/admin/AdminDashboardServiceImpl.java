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
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.repositories.admin.AdminInvoiceStatsRepository;
import sunshine_dental_care.repositories.admin.AdminPatientStatsRepository;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.services.interfaces.admin.AdminDashboardService;
import sunshine_dental_care.utils.WorkHoursConstants;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

        // Múi giờ Việt Nam - dùng constant từ WorkHoursConstants để đồng nhất
        private static final ZoneId VN_TIMEZONE = WorkHoursConstants.VN_TIMEZONE;

        private final UserRepo userRepo;
        private final ClinicRepo clinicRepo;
        private final LeaveRequestRepo leaveRequestRepo;
        private final AdminInvoiceStatsRepository adminInvoiceStatsRepository;
        private final AdminPatientStatsRepository adminPatientStatsRepository;
        private final sunshine_dental_care.repositories.hr.AttendanceRepository attendanceRepo;
        private final AppointmentRepo appointmentRepo;

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
                                // Tính doanh số từ ProductInvoice (tất cả hóa đơn)
                                BigDecimal todayProductSales = adminInvoiceStatsRepository.sumTotalSalesBetween(today, today);
                                BigDecimal weekProductSales = adminInvoiceStatsRepository.sumTotalSalesBetween(weekStart, today);
                                BigDecimal monthProductSales = adminInvoiceStatsRepository.sumTotalSalesBetween(monthStart, today);
                                BigDecimal prevMonthProductSales = adminInvoiceStatsRepository.sumTotalSalesBetween(prevMonthStart, prevMonthEnd);
                                
                                // Tính doanh số từ Appointments (dịch vụ khám) - chuyển LocalDate sang Instant
                                Instant todayStartInstant = today.atStartOfDay(VN_TIMEZONE).toInstant();
                                Instant todayEndInstant = today.plusDays(1).atStartOfDay(VN_TIMEZONE).toInstant();
                                Instant weekStartInstant = weekStart.atStartOfDay(VN_TIMEZONE).toInstant();
                                Instant monthStartInstant = monthStart.atStartOfDay(VN_TIMEZONE).toInstant();
                                Instant todayInstant = LocalDate.now(VN_TIMEZONE).atStartOfDay(VN_TIMEZONE).plusDays(1).toInstant();
                                Instant prevMonthStartInstant = prevMonthStart.atStartOfDay(VN_TIMEZONE).toInstant();
                                Instant prevMonthEndInstant = prevMonthEnd.plusDays(1).atStartOfDay(VN_TIMEZONE).toInstant();
                                
                                BigDecimal todayAppointmentSales = appointmentRepo.sumRevenueFromAppointments(todayStartInstant, todayEndInstant);
                                BigDecimal weekAppointmentSales = appointmentRepo.sumRevenueFromAppointments(weekStartInstant, todayInstant);
                                BigDecimal monthAppointmentSales = appointmentRepo.sumRevenueFromAppointments(monthStartInstant, todayInstant);
                                BigDecimal prevMonthAppointmentSales = appointmentRepo.sumRevenueFromAppointments(prevMonthStartInstant, prevMonthEndInstant);
                                
                                // Tổng doanh số = ProductInvoice + Appointments
                                todayTotalSales = todayProductSales.add(todayAppointmentSales);
                                weekTotalSales = weekProductSales.add(weekAppointmentSales);
                                monthTotalSales = monthProductSales.add(monthAppointmentSales);
                                prevMonthTotalSales = prevMonthProductSales.add(prevMonthAppointmentSales);
                                
                                // Tính tiền thực thu từ ProductInvoice (chỉ đã thanh toán)
                                BigDecimal todayProductRevenue = adminInvoiceStatsRepository.sumRevenueBetween(today, today);
                                BigDecimal weekProductRevenue = adminInvoiceStatsRepository.sumRevenueBetween(weekStart, today);
                                BigDecimal monthProductRevenue = adminInvoiceStatsRepository.sumRevenueBetween(monthStart, today);
                                BigDecimal prevMonthProductRevenue = adminInvoiceStatsRepository.sumRevenueBetween(prevMonthStart, prevMonthEnd);
                                
                                // Tính tiền thực thu từ Appointments (đã thanh toán) - dùng cùng Instant như trên
                                BigDecimal todayAppointmentRevenue = appointmentRepo.sumRevenueFromAppointments(todayStartInstant, todayEndInstant);
                                BigDecimal weekAppointmentRevenue = appointmentRepo.sumRevenueFromAppointments(weekStartInstant, todayInstant);
                                BigDecimal monthAppointmentRevenue = appointmentRepo.sumRevenueFromAppointments(monthStartInstant, todayInstant);
                                BigDecimal prevMonthAppointmentRevenue = appointmentRepo.sumRevenueFromAppointments(prevMonthStartInstant, prevMonthEndInstant);
                                
                                // Tổng thực thu = ProductInvoice + Appointments
                                todayRevenue = todayProductRevenue.add(todayAppointmentRevenue);
                                weekRevenue = weekProductRevenue.add(weekAppointmentRevenue);
                                monthRevenue = monthProductRevenue.add(monthAppointmentRevenue);
                                prevMonthRevenue = prevMonthProductRevenue.add(prevMonthAppointmentRevenue);
                                
                                totalExpenses = calculateExpenses(monthStart, today);
                                // Module chi phí chưa được implement, expensesSupported = false
                                expensesSupported = false;
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
                                // Log đầy đủ với stack trace và throw lại để không giấu lỗi
                                log.error("Lỗi nghiêm trọng khi tính doanh thu. Các giá trị doanh thu có thể không chính xác. " +
                                        "Error: {}", e.getMessage(), e);
                                // Không throw để tránh làm crash toàn bộ dashboard, nhưng log rõ ràng
                                // Các giá trị sẽ giữ nguyên = 0 (đã khởi tạo ở trên)
                        }

                        // Thống kê lịch hẹn
                        Long todayAppointments = 0L;
                        Long weekAppointments = 0L;
                        Long monthAppointments = 0L;
                        Long todayCancelledAppointments = 0L;
                        Map<String, Long> appointmentsByStatus = new HashMap<>();
                        try {
                                // Đếm appointments hôm nay (theo startDateTime)
                                todayAppointments = appointmentRepo.countByStartDateTimeBetween(dayStart, dayEnd);
                                
                                // Đếm appointments trong tuần
                                weekAppointments = appointmentRepo.countByStartDateTimeBetween(weekRangeStart, weekEnd);
                                
                                // Đếm appointments trong tháng
                                monthAppointments = appointmentRepo.countByStartDateTimeBetween(monthRangeStart, monthRangeEnd);
                                
                                // Đếm appointments đã hủy hôm nay
                                todayCancelledAppointments = appointmentRepo.countByStartDateTimeBetweenAndStatus(
                                        dayStart, dayEnd, "CANCELLED");
                                
                                // Thống kê appointments theo status
                                List<Appointment> allAppointments = appointmentRepo.findByStartDateTimeBetween(monthRangeStart, monthRangeEnd);
                                appointmentsByStatus = allAppointments.stream()
                                        .collect(Collectors.groupingBy(
                                                Appointment::getStatus,
                                                Collectors.counting()
                                        ));
                        } catch (Exception e) {
                                log.error("Lỗi khi tính thống kê lịch hẹn. Các giá trị appointments có thể không chính xác. " +
                                        "Error: {}", e.getMessage(), e);
                        }

                        // Tính toán sourceBreakdown: đếm số lượng unique patients theo channel trong tháng
                        Map<String, Long> sourceBreakdown = new HashMap<>();
                        try {
                                // Query lại appointments trong tháng để tính sourceBreakdown
                                List<Appointment> monthAppointmentsForSource = appointmentRepo.findByStartDateTimeBetween(monthRangeStart, monthRangeEnd);
                                
                                sourceBreakdown = monthAppointmentsForSource.stream()
                                                .filter(a -> a.getPatient() != null) // Đảm bảo có patient
                                                .collect(Collectors.groupingBy(
                                                                a -> {
                                                                        // Normalize channel: trim, uppercase, và xử lý null/empty
                                                                        String channel = a.getChannel();
                                                                        if (channel == null || channel.trim().isEmpty()) {
                                                                                return "WALK_IN"; // Default cho appointments cũ không có channel
                                                                        }
                                                                        return channel.trim().toUpperCase();
                                                                },
                                                                Collectors.mapping(
                                                                                a -> a.getPatient().getId(),
                                                                                Collectors.collectingAndThen(
                                                                                                Collectors.toSet(),
                                                                                                set -> (long) set.size()
                                                                                )
                                                                )
                                                ));
                                
                                // Đảm bảo các channel chính có trong map (nếu không có thì = 0)
                                String[] mainChannels = {"WEB_BOOKING", "WALK_IN", "PHONE"};
                                for (String channel : mainChannels) {
                                        sourceBreakdown.putIfAbsent(channel, 0L);
                                }
                        } catch (Exception e) {
                                log.error("❌ Lỗi khi tính thống kê nguồn khách. sourceBreakdown có thể trống. " +
                                                "Error: {}", e.getMessage(), e);
                                // Đảm bảo vẫn có các channel chính ngay cả khi có lỗi
                                String[] mainChannels = {"WEB_BOOKING", "WALK_IN", "PHONE"};
                                for (String channel : mainChannels) {
                                        sourceBreakdown.putIfAbsent(channel, 0L);
                                }
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
                                monthNewPatients = adminPatientStatsRepository.countCreatedBetween(monthRangeStart, monthRangeEnd);
                        } catch (Exception e) {
                                log.error("Lỗi nghiêm trọng khi đếm bệnh nhân. Các giá trị patients có thể không chính xác. " +
                                        "Error: {}", e.getMessage(), e);
                        }

                        // Thống kê nhân viên, phòng khám
                        Long totalStaff = 0L;
                        Long totalClinics = 0L;
                        Long activeClinics = 0L;
                        try {
                                totalStaff = userRepo.count();
                                totalClinics = clinicRepo.count();
                                activeClinics = clinicRepo.countByIsActiveTrue();
                        } catch (Exception e) {
                                log.error("Lỗi nghiêm trọng khi đếm nhân viên/phòng khám. Các giá trị staff/clinics có thể không chính xác. " +
                                        "Error: {}", e.getMessage(), e);
                        }

                        // Lấy số bản ghi chấm công hôm nay
                        Long todayAttendance = 0L;
                        try {
                                todayAttendance = attendanceRepo.countByWorkDate(today);
                        } catch (Exception e) {
                                log.error("Lỗi khi lấy số chấm công hôm nay. Giá trị attendance có thể không chính xác. " +
                                        "Error: {}", e.getMessage(), e);
                        }

                        // Số đơn xin nghỉ phép đang chờ xử lý
                        Long pendingLeaveRequests = 0L;
                        try {
                                pendingLeaveRequests = leaveRequestRepo.countByStatus("PENDING");
                        } catch (Exception e) {
                                log.error("Lỗi khi lấy số đơn nghỉ phép. Giá trị pendingLeaveRequests có thể không chính xác. " +
                                        "Error: {}", e.getMessage(), e);
                        }

                        // Tỷ lệ quay lại khám/thống kê nguồn khách của bệnh nhân trong tháng
                        double retentionRate = 0.0;
                        long returningPatients = 0L;
                        long patientsThisMonth = 0L;
                        try {
                                // Lấy tất cả appointments trong tháng hiện tại
                                List<Appointment> monthAppointmentsForRetention = appointmentRepo.findByStartDateTimeBetween(monthRangeStart, monthRangeEnd);
                                
                                // Tính số unique patients trong tháng này
                                java.util.Set<Integer> patientsInThisMonth = monthAppointmentsForRetention.stream()
                                                .filter(a -> a.getPatient() != null)
                                                .map(a -> a.getPatient().getId())
                                                .collect(Collectors.toSet());
                                patientsThisMonth = patientsInThisMonth.size();
                                
                                if (patientsThisMonth > 0) {
                                        // Tính số patients đã từng khám TRƯỚC tháng này
                                        // Lấy tất cả appointments trước tháng này
                                        Instant beforeMonthStart = toStartOfDay(monthStart.minusDays(1));
                                        List<Appointment> previousAppointments = appointmentRepo.findByStartDateTimeBetween(
                                                        Instant.ofEpochMilli(0), // Từ đầu
                                                        beforeMonthStart
                                        );
                                        
                                        java.util.Set<Integer> patientsBeforeThisMonth = previousAppointments.stream()
                                                        .filter(a -> a.getPatient() != null)
                                                        .map(a -> a.getPatient().getId())
                                                        .collect(Collectors.toSet());
                                        
                                        // Returning patients = patients có trong tháng này VÀ đã từng khám trước đó
                                        returningPatients = patientsInThisMonth.stream()
                                                        .filter(patientsBeforeThisMonth::contains)
                                                        .count();
                                        
                                        // Tính retention rate
                                        retentionRate = (returningPatients * 100.0) / patientsThisMonth;
                                }
                        } catch (Exception e) {
                                log.error("❌ Lỗi khi tính retention rate. Các giá trị retention có thể không chính xác. " +
                                                "Error: {}", e.getMessage(), e);
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
                                log.error("Lỗi khi tính top bác sĩ. Danh sách topDoctors có thể trống. " +
                                        "Error: {}", e.getMessage(), e);
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

        // Chuyển LocalDate -> Instant ở mốc đầu ngày (theo múi giờ Việt Nam)
        private Instant toStartOfDay(LocalDate date) {
                return date.atStartOfDay(VN_TIMEZONE).toInstant();
        }

        // Hàm tính tổng chi phí (hiện tại chưa có module chi phí)
        private BigDecimal calculateExpenses(LocalDate startDate, LocalDate endDate) {
                // Log warning rõ ràng thay vì return 0 im lặng
                log.warn("Module quản lý chi phí chưa được triển khai. Expenses sẽ trả về 0. " +
                        "Cần implement calculateExpenses() với dữ liệu thực tế từ bảng Expenses. " +
                        "Date range: {} to {}", startDate, endDate);
                return BigDecimal.ZERO;
        }
}
