package sunshine_dental_care.api.admin;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.services.interfaces.admin.AdminAppointmentService;

@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppointmentController {

    private final AdminAppointmentService adminAppointmentService;

    //ấy danh sách lịch hẹn trong ngày.
    
    @GetMapping
    public ResponseEntity<List<AppointmentResponse>> getAppointments(
            @RequestParam(value = "date", required = true)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "clinicId", required = false) Integer clinicId) {
        
        List<AppointmentResponse> appointments = adminAppointmentService.getAppointments(date, clinicId);
        return ResponseEntity.ok(appointments);
    }
}
