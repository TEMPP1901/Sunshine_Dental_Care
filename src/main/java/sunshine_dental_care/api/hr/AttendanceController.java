package sunshine_dental_care.api.hr;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckInRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckOutRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.DailySummaryResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlySummaryResponse;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;

@RestController
@RequestMapping("/api/hr/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {
    
    private final AttendanceService attendanceService;
    
    /**
     * Chấm công vào (check-in)
     * POST /api/hr/attendance/check-in
     * 
     * Body: AttendanceCheckInRequest
     * - userId, clinicId, faceEmbedding (required)
     * - ssid, bssid, deviceId, ipAddr, lat, lng, note (optional)
     */
    @PostMapping("/check-in")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> checkIn(@Valid @RequestBody AttendanceCheckInRequest request) {
        log.info("Check-in request from user {} at clinic {}", request.getUserId(), request.getClinicId());
        AttendanceResponse response = attendanceService.checkIn(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Chấm công ra (check-out)
     * POST /api/hr/attendance/check-out
     */
    @PostMapping("/check-out")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> checkOut(@Valid @RequestBody AttendanceCheckOutRequest request) {
        log.info("Check-out request for attendance {}", request.getAttendanceId());
        AttendanceResponse response = attendanceService.checkOut(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy attendance hôm nay của user
     * GET /api/hr/attendance/today?userId=123
     */
    @GetMapping("/today")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> getTodayAttendance(
            @RequestParam Integer userId) {
        AttendanceResponse response = attendanceService.getTodayAttendance(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy lịch sử attendance (có phân trang)
     * GET /api/hr/attendance/history?userId=123&clinicId=1&startDate=2024-01-01&endDate=2024-01-31&page=0&size=10
     */
    @GetMapping("/history")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Page<AttendanceResponse>> getAttendanceHistory(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // Default date range: last 30 days
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        Page<AttendanceResponse> response = attendanceService.getAttendanceHistory(
            userId, clinicId, startDate, endDate, page, size);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy thống kê attendance
     * GET /api/hr/attendance/statistics?userId=123&clinicId=1&startDate=2024-01-01&endDate=2024-01-31
     */
    @GetMapping("/statistics")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> getAttendanceStatistics(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        // Default date range: last 30 days
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        Map<String, Object> stats = attendanceService.getAttendanceStatistics(
            userId, clinicId, startDate, endDate);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Lấy chi tiết attendance theo ID
     * GET /api/hr/attendance/{id}
     */
    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> getAttendanceById(@PathVariable Integer id) {
        AttendanceResponse response = attendanceService.getAttendanceById(id);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Lấy Daily Summary - Tổng hợp attendance theo department trong ngày
     * GET /api/hr/attendance/daily-summary?workDate=2024-01-01
     */
    @GetMapping("/daily-summary")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<java.util.List<DailySummaryResponse>> getDailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        
        if (workDate == null) {
            workDate = LocalDate.now();
        }
        
        java.util.List<DailySummaryResponse> summaries = attendanceService.getDailySummary(workDate);
        return ResponseEntity.ok(summaries);
    }
    
    /**
     * Lấy Daily Attendance List - Danh sách chi tiết từng nhân viên trong ngày
     * GET /api/hr/attendance/daily-list?workDate=2024-01-01&departmentId=1&clinicId=1&page=0&size=10
     */
    @GetMapping("/daily-list")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<Page<DailyAttendanceListItemResponse>> getDailyAttendanceList(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        if (workDate == null) {
            workDate = LocalDate.now();
        }
        
        Page<DailyAttendanceListItemResponse> items = attendanceService.getDailyAttendanceList(
            workDate, departmentId, clinicId, page, size);
        return ResponseEntity.ok(items);
    }
    
    /**
     * Lấy Monthly Summary - Tổng hợp attendance theo department trong tháng
     * GET /api/hr/attendance/monthly-summary?year=2025&month=11
     */
    @GetMapping("/monthly-summary")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<java.util.List<MonthlySummaryResponse>> getMonthlySummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (month == null) {
            month = LocalDate.now().getMonthValue();
        }
        
        java.util.List<MonthlySummaryResponse> summaries = attendanceService.getMonthlySummary(year, month);
        return ResponseEntity.ok(summaries);
    }
    
    /**
     * Lấy Monthly Attendance List - Danh sách chi tiết từng nhân viên trong tháng
     * GET /api/hr/attendance/monthly-list?year=2025&month=11&departmentId=1&clinicId=1&page=0&size=10
     */
    @GetMapping("/monthly-list")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<Page<MonthlyAttendanceListItemResponse>> getMonthlyAttendanceList(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (month == null) {
            month = LocalDate.now().getMonthValue();
        }
        
        Page<MonthlyAttendanceListItemResponse> items = attendanceService.getMonthlyAttendanceList(
            year, month, departmentId, clinicId, page, size);
        return ResponseEntity.ok(items);
    }
}

