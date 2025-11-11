package sunshine_dental_care.services.interfaces.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.hrDTO.AttendanceCheckInRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckOutRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.DailySummaryResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlySummaryResponse;

/**
 * Service interface cho Attendance management
 */
public interface AttendanceService {
    
    /**
     * Chấm công vào (check-in)
     * Validate face, WiFi, và tạo Attendance record
     */
    AttendanceResponse checkIn(AttendanceCheckInRequest request);
    
    /**
     * Chấm công ra (check-out)
     */
    AttendanceResponse checkOut(AttendanceCheckOutRequest request);
    
    /**
     * Lấy attendance của user trong ngày hôm nay
     */
    AttendanceResponse getTodayAttendance(Integer userId);
    
    /**
     * Lấy lịch sử attendance của user (có phân trang)
     */
    Page<AttendanceResponse> getAttendanceHistory(
        Integer userId, 
        Integer clinicId,
        LocalDate startDate, 
        LocalDate endDate,
        int page, 
        int size
    );
    
    /**
     * Lấy thống kê attendance
     */
    Map<String, Object> getAttendanceStatistics(
        Integer userId,
        Integer clinicId,
        LocalDate startDate,
        LocalDate endDate
    );
    
    /**
     * Lấy chi tiết attendance theo ID
     */
    AttendanceResponse getAttendanceById(Integer attendanceId);
    
    /**
     * Lấy Daily Summary - Tổng hợp attendance theo department trong ngày
     */
    List<DailySummaryResponse> getDailySummary(LocalDate workDate);
    
    /**
     * Lấy Daily Attendance List - Danh sách chi tiết từng nhân viên trong ngày
     */
    Page<DailyAttendanceListItemResponse> getDailyAttendanceList(
        LocalDate workDate, Integer departmentId, Integer clinicId, int page, int size);
    
    /**
     * Lấy Monthly Summary - Tổng hợp attendance theo department trong tháng
     * @param year Năm (ví dụ: 2025)
     * @param month Tháng (1-12)
     */
    List<MonthlySummaryResponse> getMonthlySummary(Integer year, Integer month);
    
    /**
     * Lấy Monthly Attendance List - Danh sách chi tiết từng nhân viên trong tháng
     * @param year Năm (ví dụ: 2025)
     * @param month Tháng (1-12)
     * @param departmentId ID phòng ban (optional)
     * @param clinicId ID phòng khám (optional)
     * @param page Số trang (bắt đầu từ 0)
     * @param size Số lượng items mỗi trang
     */
    Page<MonthlyAttendanceListItemResponse> getMonthlyAttendanceList(
        Integer year, Integer month, Integer departmentId, Integer clinicId, int page, int size);

    /**
     * Admin view - lấy danh sách attendance theo ngày/clinic/status
     */
    List<AttendanceResponse> getAttendanceForAdmin(LocalDate workDate, Integer clinicId, String status);

    /**
     * Admin cập nhật trạng thái và ghi chú phê duyệt
     */
    AttendanceResponse updateAttendanceStatus(Integer attendanceId, String newStatus, String adminNote, Integer adminUserId);
}

