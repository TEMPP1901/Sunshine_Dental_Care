package sunshine_dental_care.api.admin;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AttendanceStatusUpdateRequest;
import sunshine_dental_care.dto.hrDTO.AdminExplanationActionRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationResponse;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    public ResponseEntity<List<AttendanceResponse>> getAttendanceList(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "clinicId", required = false) Integer clinicId,
            @RequestParam(value = "status", required = false) String status) {

        List<AttendanceResponse> responses = attendanceService.getAttendanceForAdmin(date, clinicId, status);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AttendanceResponse> updateAttendanceStatus(
            @PathVariable Integer id,
            @Validated @RequestBody AttendanceStatusUpdateRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        Integer adminId = currentUser != null ? currentUser.userId() : null;
        AttendanceResponse response = attendanceService.updateAttendanceStatus(
            id,
            request.getNewStatus(),
            request.getAdminNote(),
            adminId
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Admin xem danh sách giải trình đang chờ xử lý
     * GET /api/admin/attendance/explanations/pending?clinicId=1
     */
    @GetMapping("/explanations/pending")
    public ResponseEntity<List<AttendanceExplanationResponse>> getPendingExplanations(
            @RequestParam(required = false) Integer clinicId) {
        List<AttendanceExplanationResponse> explanations = 
                attendanceService.getPendingExplanations(clinicId);
        return ResponseEntity.ok(explanations);
    }
    
    /**
     * Admin approve/reject giải trình và tự động cập nhật attendance status
     * POST /api/admin/attendance/explanations/process
     * Body: { "attendanceId": 123, "action": "APPROVE" hoặc "REJECT", "adminNote": "..." }
     * 
     * Khi APPROVE:
     * - LATE → APPROVED_LATE (tính cả ngày)
     * - ABSENT/MISSING_CHECK_IN → APPROVED_ABSENCE (tính cả ngày)
     * - MISSING_CHECK_OUT → Giữ nguyên status (tính cả ngày)
     */
    @PostMapping("/explanations/process")
    public ResponseEntity<AttendanceResponse> processExplanation(
            @Validated @RequestBody AdminExplanationActionRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        Integer adminId = currentUser != null ? currentUser.userId() : null;
        AttendanceResponse response = attendanceService.processExplanation(request, adminId);
        return ResponseEntity.ok(response);
    }
}

