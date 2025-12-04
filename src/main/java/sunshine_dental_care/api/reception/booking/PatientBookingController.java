package sunshine_dental_care.api.reception.booking;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.SessionAvailabilityResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.reception.BookingService;
import sunshine_dental_care.services.interfaces.reception.ReceptionService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class PatientBookingController {
    private final BookingService bookingService;
    private final ReceptionService receptionService;

    @GetMapping("/slots")
    public ResponseEntity<List<TimeSlotResponse>> getAvailableSlots(
            @Valid @ModelAttribute BookingSlotRequest request) {
        return ResponseEntity.ok(bookingService.getAvailableSlots(request));
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody AppointmentRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {

        // Tái sử dụng logic tạo lịch của ReceptionService
        // VìCurrentUser là Patient, logic bên trong sẽ lấy ID của người này
        return ResponseEntity.ok(receptionService.createNewAppointment(currentUser, request));
    }

    @GetMapping("/availability")
    public ResponseEntity<SessionAvailabilityResponse> checkAvailability(
            @RequestParam Integer clinicId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(bookingService.checkSessionAvailability(clinicId, date));
    }
}
