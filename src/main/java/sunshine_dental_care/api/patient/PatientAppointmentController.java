package sunshine_dental_care.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.services.interfaces.patient.PatientService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patient/appointments")
@RequiredArgsConstructor
public class PatientAppointmentController {

    private final PatientService patientService;

    // 1. Lấy danh sách lịch hẹn
    @GetMapping
    public ResponseEntity<List<PatientAppointmentResponse>> getMyAppointments() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName(); // Lấy email từ token
        return ResponseEntity.ok(patientService.getMyAppointments(email));
    }

    // 2. Hủy lịch hẹn
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