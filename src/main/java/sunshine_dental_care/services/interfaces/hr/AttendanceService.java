package sunshine_dental_care.services.interfaces.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.hrDTO.AdminExplanationActionRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckInRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckOutRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationResponse;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.DailySummaryResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlySummaryResponse;

// Service interface for Attendance Management
public interface AttendanceService {

    // Chấm công vào, validate khuôn mặt, WiFi và tạo bản ghi chấm công
    AttendanceResponse checkIn(AttendanceCheckInRequest request);

    // Chấm công ra
    AttendanceResponse checkOut(AttendanceCheckOutRequest request);

    // Lấy thông tin chấm công của user trong ngày hiện tại
    AttendanceResponse getTodayAttendance(Integer userId);

    // Lấy danh sách tất cả attendance của user trong ngày hiện tại (cho bác sĩ có nhiều ca)
    List<AttendanceResponse> getTodayAttendanceList(Integer userId);

    Page<AttendanceResponse> getAttendanceHistory(
        Integer userId,
        Integer clinicId,
        LocalDate startDate,
        LocalDate endDate,
        int page,
        int size
    );

    Map<String, Object> getAttendanceStatistics(
        Integer userId,
        Integer clinicId,
        LocalDate startDate,
        LocalDate endDate
    );

    // Lấy chi tiết attendance theo id
    AttendanceResponse getAttendanceById(Integer attendanceId);

    // Lấy tổng hợp attendance theo phòng ban trong ngày
    List<DailySummaryResponse> getDailySummary(LocalDate workDate);

    Page<DailyAttendanceListItemResponse> getDailyAttendanceList(
        LocalDate workDate, Integer departmentId, Integer clinicId, int page, int size);

    // Lấy tổng hợp attendance theo phòng ban trong tháng
    List<MonthlySummaryResponse> getMonthlySummary(Integer year, Integer month);

    Page<MonthlyAttendanceListItemResponse> getMonthlyAttendanceList(
        Integer year, Integer month, Integer departmentId, Integer clinicId, int page, int size);

    // Admin lấy danh sách chấm công theo ngày/phòng khám/trạng thái
    List<AttendanceResponse> getAttendanceForAdmin(LocalDate workDate, Integer clinicId, String status);

    // Admin cập nhật trạng thái và ghi chú của chấm công
    AttendanceResponse updateAttendanceStatus(Integer attendanceId, String newStatus, String adminNote, Integer adminUserId);

    // Nhân viên xem danh sách chấm công cần giải trình
    List<AttendanceExplanationResponse> getAttendanceNeedingExplanation(Integer userId);

    // Nhân viên gửi giải trình
    AttendanceResponse submitExplanation(AttendanceExplanationRequest request, Integer userId);

    // Admin xem danh sách các giải trình đang chờ duyệt
    List<AttendanceExplanationResponse> getPendingExplanations(Integer clinicId);

    // Admin duyệt hoặc từ chối giải trình và cập nhật trạng thái attendance
    AttendanceResponse processExplanation(AdminExplanationActionRequest request, Integer adminUserId);
}
