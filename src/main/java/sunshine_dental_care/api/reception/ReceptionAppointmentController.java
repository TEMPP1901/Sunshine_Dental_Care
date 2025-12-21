package sunshine_dental_care.api.reception;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.AppointmentUpdateRequest;
import sunshine_dental_care.dto.receptionDTO.BillInvoiceDTO;
import sunshine_dental_care.dto.receptionDTO.PatientHistoryDTO;
import sunshine_dental_care.dto.receptionDTO.PatientRequest;
import sunshine_dental_care.dto.receptionDTO.PatientResponse;
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
            @RequestParam(required = false) Integer roomId) {

        AppointmentResponse response = receptionService.assignRoomToAppointment(id, roomId);
        return ResponseEntity.ok(response);
    }

    // 1. API Xem chi tiết hóa đơn (Preview trước khi in)
    // Gọi khi Lễ tân bấm nút "Thanh Toán" trên giao diện
    @GetMapping("/appointments/{id}/bill")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<BillInvoiceDTO> getBillDetails(@PathVariable Integer id) {
        BillInvoiceDTO bill = receptionService.getBillDetails(id);
        return ResponseEntity.ok(bill);
    }

    // 2. API Xác nhận thanh toán (Chốt đơn)
    // Gọi khi Lễ tân đã nhận tiền và bấm "Xác Nhận"
    @PostMapping("/appointments/{id}/pay")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<?> confirmPayment(
            @PathVariable Integer id,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        receptionService.confirmPayment(currentUser, id);

        // Trả về thông báo thành công
        return ResponseEntity.ok(Map.of("message", "Xác nhận thanh toán thành công!"));
    }

    // API Lấy danh sách cho bảng AppointmentList.tsx
    @GetMapping("/appointments/search")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<Page<AppointmentResponse>> getAppointmentList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String paymentStatus, // Lọc trạng thái tiền
            @RequestParam(required = false) String status,        // Lọc trạng thái khám
            @RequestParam(required = false) LocalDate date,       // Lọc ngày (null = xem hết)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CurrentUser currentUser // lấy clinicId tự động
    ) {
        return ResponseEntity.ok(
                receptionService.getAppointmentList(currentUser, keyword, paymentStatus, status, date, page, size)
        );
    }

    // 1. API Lấy chi tiết hồ sơ bệnh nhân
    @GetMapping("/patients/{id}")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<PatientResponse> getPatientDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(receptionService.getPatientDetail(id));
    }

    // 2. API Cập nhật thông tin bệnh nhân
    @PutMapping("/patients/{id}")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable Integer id,
            @RequestBody PatientResponse request // Dùng lại PatientResponse làm request body
    ) {
        return ResponseEntity.ok(receptionService.updatePatient(id, request));
    }

    // 3. API Lấy lịch sử khám bệnh (History)
    @GetMapping("/patients/{id}/history")
    @PreAuthorize("hasAnyRole('RECEPTION', 'ADMIN')")
    public ResponseEntity<List<PatientHistoryDTO>> getPatientHistory(@PathVariable Integer id) {
        return ResponseEntity.ok(receptionService.getPatientHistory(id));
    }

    // API Lấy danh sách hóa đơn (Invoices)
    @GetMapping("/invoices")
    public ResponseEntity<Page<AppointmentResponse>> getInvoiceList(
            @AuthenticationPrincipal CurrentUser currentUser, // Dùng @AuthenticationPrincipal chuẩn của Spring Security
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<AppointmentResponse> result = receptionService.getInvoiceList(
                currentUser, keyword, fromDate, toDate, paymentStatus, page, size
        );
        return ResponseEntity.ok(result);
    }
}