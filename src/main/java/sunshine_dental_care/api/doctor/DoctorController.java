package sunshine_dental_care.api.doctor;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.services.doctor.DoctorAppointmentService;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {
    @Autowired
    private final DoctorAppointmentService doctorAppointmentService;

    @GetMapping("/appointments/{id}")
    public ResponseEntity<List<DoctorAppointmentDTO>> getAppointmentsByDoctorId(@PathVariable Integer id) {
        List<DoctorAppointmentDTO> appointments = doctorAppointmentService.findByDoctorId(id);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/appointments/{id}/status/{status}")
    public ResponseEntity<List<DoctorAppointmentDTO>> getAppointmentsByDoctorIdAndStatus(
            @PathVariable Integer id,
            @PathVariable String status) {
        List<DoctorAppointmentDTO> appointments = doctorAppointmentService.findByDoctorIdAndStatus(id, status);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/appointments/{doctorId}/{appointmentId}")
    public ResponseEntity<DoctorAppointmentDTO> getAppointmentDetail(
            @PathVariable Integer doctorId,
            @PathVariable Integer appointmentId) {
        DoctorAppointmentDTO appointment = doctorAppointmentService.findByIdAndDoctorId(appointmentId, doctorId);
        return ResponseEntity.ok(appointment);
    }

    @PostMapping("/appointments/{appointmentId}/status")
    public ResponseEntity<Void> changeAppointmentStatus(
            @PathVariable Integer appointmentId,
            @RequestParam String status) {
        doctorAppointmentService.changeStatusAppointment(appointmentId, status);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/appointments/{doctorId}/date-range")
    public ResponseEntity<List<DoctorAppointmentDTO>> getAppointmentsByDoctorIdAndDateRange(
            @PathVariable Integer doctorId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        Instant start = Instant.parse(startDate);
        Instant end = Instant.parse(endDate);

        List<DoctorAppointmentDTO> appointments =
                doctorAppointmentService.findByDoctorIdAndStartDateTimeBetween(doctorId, start, end);

        return ResponseEntity.ok(appointments);
    }

}
