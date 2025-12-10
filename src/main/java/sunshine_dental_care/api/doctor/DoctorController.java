package sunshine_dental_care.api.doctor;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.doctor.DoctorAppointmentService;
import sunshine_dental_care.services.interfaces.hr.HrService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {
    @Autowired
    private final DoctorAppointmentService doctorAppointmentService;
    
    @Autowired
    private final HrService hrService;

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

        // Parse linh hoạt: hỗ trợ cả định dạng chỉ ngày (yyyy-MM-dd) và ISO-8601 đầy đủ
        Instant start = parseToInstant(startDate);
        Instant end = parseToInstant(endDate);

        List<  DoctorAppointmentDTO> appointments =
                doctorAppointmentService.findByDoctorIdAndStartDateTimeBetween(doctorId, start, end);

        return ResponseEntity.ok(appointments);
    }

    /**
     * Chuyển đổi chuỗi ngày tháng sang Instant
     * Hỗ trợ 2 định dạng:
     * 1. Chỉ ngày: "2025-11-18" -> tự động thêm 00:00:00 UTC
     * 2. ISO-8601 đầy đủ: "2025-11-18T10:30:00Z" hoặc "2025-11-18T10:30:00+07:00"
     */
    private Instant parseToInstant(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        try {
            // Thử parse ISO-8601 đầy đủ trước (có thời gian và timezone)
            return Instant.parse(dateString);
        } catch (DateTimeParseException e) {
            try {
                // Nếu không được, thử parse chỉ ngày (yyyy-MM-dd)
                LocalDate localDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
                // Chuyển sang Instant: bắt đầu ngày (00:00:00) ở UTC
                return localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException(
                    "Invalid date format. Expected 'yyyy-MM-dd' or ISO-8601 format (e.g., '2025-11-18T10:30:00Z')",
                    e2
                );
            }
        }
    }


    @GetMapping("/my-schedule/{weekStart}")
    public ResponseEntity<List<DoctorScheduleDto>> getMySchedule(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser)) {
            return ResponseEntity.status(401).build();
        }
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        List<DoctorScheduleDto> schedules = hrService.getMySchedule(currentUser.userId(), weekStart);
        return ResponseEntity.ok(schedules);
    }

}
