package sunshine_dental_care.api.reception;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.*;
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

    @GetMapping("/patients")
    public ResponseEntity<Page<PatientResponse>> getPatients(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(receptionService.getPatients(keyword, page, size));
    }

    /**
     * 2. Tạo nhanh hồ sơ bệnh nhân (Walk-in)
     * POST /api/reception/patients
     */
    @PostMapping("/patients")
    public ResponseEntity<PatientResponse> createPatient(@RequestBody @Valid PatientRequest request) {
        return ResponseEntity.ok(receptionService.createPatient(request));
    }

    // Update lịch hẹn
    @PutMapping("/appointments/{id}")
    public ResponseEntity<AppointmentResponse> updateAppointment(
            @PathVariable Integer id,
            @RequestBody AppointmentUpdateRequest request) {

        return ResponseEntity.ok(receptionService.updateAppointment(id, request));
    }

    // API: Gán phòng cho lịch hẹn
    // URL: PUT /api/reception/appointments/{id}/assign-room?roomId=5
    @PutMapping("/appointments/{id}/assign-room")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> assignRoom(
            @PathVariable Integer id,
            @RequestParam Integer roomId) {

        AppointmentResponse response = receptionService.assignRoomToAppointment(id, roomId);
        return ResponseEntity.ok(response);
    }
}