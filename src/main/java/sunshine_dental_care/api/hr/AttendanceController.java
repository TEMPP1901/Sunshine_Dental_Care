package sunshine_dental_care.api.hr;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;

@RestController
@RequestMapping("/api/hr/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final FaceRecognitionService faceRecognitionService;

    // Chấm công vào
    @PostMapping("/check-in")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> checkIn(@Valid @RequestBody AttendanceCheckInRequest request) {
        log.info("Check-in request from user {} (clinicId: {})",
                request.getUserId(),
                request.getClinicId() != null ? request.getClinicId() : "not provided, will be resolved");
        AttendanceResponse response = attendanceService.checkIn(request);
        return ResponseEntity.ok(response);
    }

    // Chấm công ra
    @PostMapping("/check-out")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> checkOut(@Valid @RequestBody AttendanceCheckOutRequest request) {
        log.info("Check-out request for attendance {}", request.getAttendanceId());
        AttendanceResponse response = attendanceService.checkOut(request);
        return ResponseEntity.ok(response);
    }

    // Nhận embedding khuôn mặt từ ảnh đầu vào
    @PostMapping("/embedding")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<java.util.Map<String, String>> extractEmbedding(
            @RequestPart("file") MultipartFile file) throws Exception {
        String embedding = faceRecognitionService.extractEmbedding(file);
        return ResponseEntity.ok(java.util.Map.of("embedding", embedding));
    }

    // Lấy thông tin WiFi từ máy chủ
    @GetMapping("/wifi-info")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<java.util.Map<String, String>> getHostWiFiInfo() {
        sunshine_dental_care.utils.WindowsWiFiUtil.WiFiInfo wifiInfo =
                sunshine_dental_care.utils.WindowsWiFiUtil.getCurrentWiFiInfo();

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("ssid", wifiInfo.getSsid() != null ? wifiInfo.getSsid() : "");
        response.put("bssid", wifiInfo.getBssid() != null ? wifiInfo.getBssid() : "");

        log.info("Host WiFi info requested: SSID={}, BSSID={}", wifiInfo.getSsid(), wifiInfo.getBssid());
        return ResponseEntity.ok(response);
    }

    // Lấy attendance của user ngày hôm nay
    @GetMapping("/today")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> getTodayAttendance(
            @RequestParam Integer userId) {
        AttendanceResponse response = attendanceService.getTodayAttendance(userId);
        // Không có attendance hôm nay là trường hợp hợp lệ, trả về 200 với null body
        // Frontend sẽ check response.data === null hoặc response.status === 200
        return ResponseEntity.ok(response);
    }

    // Lấy danh sách tất cả attendance của user ngày hôm nay (cho bác sĩ có nhiều ca)
    @GetMapping("/today-list")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<java.util.List<AttendanceResponse>> getTodayAttendanceList(
            @RequestParam Integer userId) {
        java.util.List<AttendanceResponse> responses = attendanceService.getTodayAttendanceList(userId);
        return ResponseEntity.ok(responses);
    }

    // Lấy lịch sử attendance (có phân trang, HR xem tất cả, thường chỉ xem bản thân)
    @GetMapping("/history")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('HR', 'DOCTOR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<Page<AttendanceResponse>> getAttendanceHistory(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CurrentUser currentUser) {

       
        // Nếu không có userId, nếu HR thì xem tất cả, nếu thường thì userId = currentUserId
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        boolean isHR = currentUser != null && currentUser.roles().contains("HR");

        if (userId == null) {
            if (!isHR && currentUser != null) {
                userId = currentUser.userId();
            }
        }

        Page<AttendanceResponse> response = attendanceService.getAttendanceHistory(
                userId, clinicId, startDate, endDate, page, size);
        return ResponseEntity.ok(response);
    }

    // Lấy thống kê attendance cho HR
    @GetMapping("/statistics")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> getAttendanceStatistics(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

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

    // Lấy chi tiết attendance theo id
    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> getAttendanceById(@PathVariable Integer id) {
        AttendanceResponse response = attendanceService.getAttendanceById(id);
        return ResponseEntity.ok(response);
    }

    // Lấy tổng hợp attendance theo ngày cho HR
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

    // Lấy danh sách attendance chi tiết theo ngày (có phân trang)
    @GetMapping("/daily-list")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
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

    // Lấy tổng hợp attendance theo tháng cho HR
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

    // Lấy danh sách attendance chi tiết theo tháng (có phân trang)
    @GetMapping("/monthly-list")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
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

    // Nhân viên lấy danh sách attendance cần giải trình
    @GetMapping("/explanations/needing")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<java.util.List<AttendanceExplanationResponse>> getAttendanceNeedingExplanation(
            @RequestParam Integer userId) {
        java.util.List<AttendanceExplanationResponse> explanations =
                attendanceService.getAttendanceNeedingExplanation(userId);
        return ResponseEntity.ok(explanations);
    }

    // Nhân viên gửi giải trình
    @PostMapping("/explanations/submit")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTION', 'ACCOUNTANT')")
    public ResponseEntity<AttendanceResponse> submitExplanation(
            @Valid @RequestBody AttendanceExplanationRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        Integer userId = currentUser != null ? currentUser.userId() : null;
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        AttendanceResponse response = attendanceService.submitExplanation(request, userId);
        return ResponseEntity.ok(response);
    }

    // Admin lấy danh sách giải trình đang chờ xử lý
    @GetMapping("/explanations/pending")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<java.util.List<AttendanceExplanationResponse>> getPendingExplanations(
            @RequestParam(required = false) Integer clinicId) {
        java.util.List<AttendanceExplanationResponse> explanations =
                attendanceService.getPendingExplanations(clinicId);
        return ResponseEntity.ok(explanations);
    }

    // Admin duyệt hoặc từ chối giải trình
    @PostMapping("/explanations/process")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<AttendanceResponse> processExplanation(
            @Valid @RequestBody AdminExplanationActionRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        Integer hrUserId = currentUser != null ? currentUser.userId() : null;
        if (hrUserId == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        AttendanceResponse response = attendanceService.processExplanation(request, hrUserId);
        return ResponseEntity.ok(response);
    }
}
