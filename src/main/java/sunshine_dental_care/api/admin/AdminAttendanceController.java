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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AttendanceStatusUpdateRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;

@RestController
@RequestMapping("/api/admin/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    // Lấy danh sách chấm công theo bộ lọc: ngày, phòng khám, trạng thái
    @GetMapping
    public ResponseEntity<List<AttendanceResponse>> getAttendanceList(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "clinicId", required = false) Integer clinicId,
            @RequestParam(value = "status", required = false) String status) {

        List<AttendanceResponse> responses = attendanceService.getAttendanceForAdmin(date, clinicId, status);
        return ResponseEntity.ok(responses);
    }

    // Admin cập nhật trạng thái chấm công cho nhân viên
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
}
