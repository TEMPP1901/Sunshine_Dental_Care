package sunshine_dental_care.api.doctor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.doctorDTO.AIPatientSummaryResponse;
import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.doctor.AppointmentAISummaryService;
import sunshine_dental_care.services.doctor.DoctorAppointmentService;
import sunshine_dental_care.services.interfaces.hr.HrService;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {
    @Autowired
    private final DoctorAppointmentService doctorAppointmentService;
    
    @Autowired
    private final HrService hrService;
    
    private static final Logger logger = LoggerFactory.getLogger(DoctorController.class);
    
    @Autowired
    private final AppointmentAISummaryService aiSummaryService;

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

    @GetMapping("/appointments/{doctorId}/day/{date}")
    public ResponseEntity<List<DoctorAppointmentDTO>> getAppointmentsByDoctorIdAndDay(
            @PathVariable Integer doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            // Bắt đầu ngày ở UTC
            Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            // Kết thúc là bắt đầu ngày tiếp theo (exclusive)
            Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            List<DoctorAppointmentDTO> appointments =
                    doctorAppointmentService.findByDoctorIdAndStartDateTimeBetween(doctorId, start, end);

            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            logger.error("Error fetching appointments for doctorId={} date={}", doctorId, date, e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/appointments/day/{date}")
    public ResponseEntity<List<DoctorAppointmentDTO>> getAppointmentsForCurrentDoctorByDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser)) {
            return ResponseEntity.status(401).build();
        }
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        Integer doctorId = currentUser.userId();
        try {
            Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            List<DoctorAppointmentDTO> appointments =
                    doctorAppointmentService.findByDoctorIdAndStartDateTimeBetween(doctorId, start, end);
            return ResponseEntity.ok(appointments);
        } catch (Exception e) {
            logger.error("Error fetching appointments for current doctorId={} date={}", doctorId, date, e);
            return ResponseEntity.status(500).build();
        }
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

    @GetMapping("/my-schedule/day/{date}")
    public ResponseEntity<List<DoctorScheduleDto>> getMyScheduleByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser)) {
            return ResponseEntity.status(401).build();
        }
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        List<DoctorScheduleDto> schedules = hrService.getMyScheduleByDate(currentUser.userId(), date);
        return ResponseEntity.ok(schedules);
    }

    /**
     * Get AI-powered patient summary for an appointment.
     * Triggered when doctor opens appointment detail page.
     * 
     * Flow:
     * 1. Frontend calls this endpoint when doctor opens appointment detail page
     * 2. Backend checks if user is authenticated (doctor must be logged in)
     * 3. Backend gets doctor ID from authenticated user
     * 4. Backend calls service to generate AI summary:
     *    - Service checks cache first (10-15 min TTL)
     *    - If cached, return cached summary
     *    - If not cached:
     *      a. Load appointment and validate doctor owns it
     *      b. Get patient ID from appointment
     *      c. Load recent medical records (3-5 most recent)
     *      d. Load recent appointment services (treatments)
     *      e. Build context with patient data
     *      f. Send context to Gemini AI
     *      g. Receive AI summary (JSON format)
     *      h. Cache the result for 12 minutes
     *      i. Parse and return summary
     * 5. Return summary to frontend
     * 
     * @param appointmentId Appointment ID
     * @param currentUser Authenticated doctor user
     * @return AI-generated patient summary with overview, alerts, and recent treatments
     */
    @GetMapping("/appointments/{appointmentId}/ai-summary")
    public ResponseEntity<AIPatientSummaryResponse> getAIPatientSummary(
            @PathVariable Integer appointmentId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        
        // Step 1: Check authentication - doctor must be logged in
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        
        // Step 2: Get doctor ID from authenticated user
        Integer doctorId = currentUser.userId();
        
        // Step 3: Call service to generate or retrieve cached AI summary
        // Service handles: cache check, data aggregation, AI call, caching
        AIPatientSummaryResponse summary = aiSummaryService.getAISummary(appointmentId, doctorId);
        
        // Step 4: Return summary to frontend
        return ResponseEntity.ok(summary);
    }

}
