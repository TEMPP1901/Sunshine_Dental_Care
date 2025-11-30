package sunshine_dental_care.api.reception;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.RescheduleRequest;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.reception.ReceptionService;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RECEPTION') or hasRole('ADMIN')")
public class ReceptionAppointmentController {

    private final ReceptionService receptionService;

    /**
     * API: Lấy lịch làm việc của Bác sĩ theo ngày, có thể tùy chọn Clinic ID (cho chức năng đổi cơ sở).
     * GET /api/reception/schedules?date=2025-11-17
     */
    @GetMapping("/schedules")
    public ResponseEntity<List<DoctorScheduleDto>> getDoctorSchedules(
            @RequestParam(value = "date", required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "clinicId", required = false) Integer clinicId,
            @AuthenticationPrincipal CurrentUser currentUser) {

        List<DoctorScheduleDto> schedules = receptionService.getDoctorSchedulesForView(
                currentUser,
                date,
                clinicId
        );

        return ResponseEntity.ok(schedules);
    }

    /**
     * API: Lấy danh sách Lịch hẹn (Appointments) trong ngày.
     * Dùng để hiển thị đè lên lịch làm việc.
     * GET /api/reception/appointments?date=2025-11-24&clinicId=1
     */
    @GetMapping("/appointments")
    public ResponseEntity<List<AppointmentResponse>> getAppointmentsByDate(
            @RequestParam(value = "date", required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "clinicId", required = false) Integer clinicId,
            @AuthenticationPrincipal CurrentUser currentUser) {

        List<AppointmentResponse> appointments = receptionService.getAppointmentsForDashboard(
                currentUser,
                date,
                clinicId
        );

        return ResponseEntity.ok(appointments);
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody AppointmentRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        // Gọi hàm createNewAppointment trong Service
        AppointmentResponse response = receptionService.createNewAppointment(
                currentUser,
                request
        );

        return ResponseEntity.status(201).body(response);
    }

    /**
     * API DỜI LỊCH (Reschedule) - Dùng cho tính năng Drag & Drop
     * PATCH /api/reception/appointments/{id}/reschedule
     */
    @PatchMapping("/appointments/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> rescheduleAppointment(
            @PathVariable Integer id,
            @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        AppointmentResponse response = receptionService.rescheduleAppointment(
                currentUser,
                id,
                request
        );

        return ResponseEntity.ok(response);
    }
}