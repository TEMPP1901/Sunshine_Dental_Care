package sunshine_dental_care.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingRequest; // Đã có file này thì sẽ hết lỗi
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.services.impl.reception.BookingServiceImpl;
import sunshine_dental_care.services.interfaces.patient.PatientService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patient/appointments")
@RequiredArgsConstructor
public class PatientAppointmentController {

    private final PatientService patientService;

    // Inject trực tiếp Implementation để dùng hàm notifyBookingSuccess
    private final BookingServiceImpl bookingService;

    // 1. Lấy danh sách lịch hẹn
    @GetMapping
    public ResponseEntity<List<PatientAppointmentResponse>> getMyAppointments() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return ResponseEntity.ok(patientService.getMyAppointments(email));
    }

    // ========================================================================
    // 2. [MỚI] ĐẶT LỊCH HẸN (POST) -> TÍCH HỢP GỬI MAIL
    // ========================================================================
    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody BookingRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        try {
            // Bước 1: Lưu lịch hẹn vào DB
            Appointment savedAppt = patientService.createAppointment(email, request);

            // Bước 2: Gửi Email xác nhận ngay lập tức
            // (Hàm này nằm trong BookingServiceImpl mà ta đã sửa ở bước trước)
            bookingService.notifyBookingSuccess(savedAppt);

            return ResponseEntity.ok("Đặt lịch thành công. Vui lòng kiểm tra email.");

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Lỗi hệ thống: " + e.getMessage());
        }
    }

    // 3. Hủy lịch hẹn
    @PutMapping("/{appointmentId}/cancel")
    public ResponseEntity<?> cancelAppointment(
            @PathVariable Integer appointmentId,
            @RequestBody Map<String, String> body
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        String reason = body.getOrDefault("reason", "Lý do cá nhân");

        try {
            patientService.cancelAppointment(email, appointmentId, reason);
            return ResponseEntity.ok("Hủy lịch thành công.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}