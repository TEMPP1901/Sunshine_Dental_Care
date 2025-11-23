package sunshine_dental_care.api.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.services.interfaces.hr.AiScheduleGenerationService;
import sunshine_dental_care.services.interfaces.hr.HrService;


@RestController
@RequestMapping("/api/hr/schedules")
@RequiredArgsConstructor
public class HrController {
    
    private final HrService hrService;
    private final AiScheduleGenerationService aiScheduleGenerationService;
    
    // 1. TẠO LỊCH MỚI cho tuần
    @PostMapping("/create")
    public ResponseEntity<List<DoctorScheduleDto>> createWeeklySchedule(@Valid @RequestBody CreateWeeklyScheduleRequest request) {
        List<DoctorScheduleDto> schedules = hrService.createWeeklySchedule(request);
        return ResponseEntity.ok(schedules);
    }
    
    // 2. XEM LỊCH HIỆN TẠI
    @GetMapping("/current")
    public ResponseEntity<List<DoctorScheduleDto>> getCurrentWeekSchedule() {
        List<DoctorScheduleDto> schedules = hrService.getCurrentWeekSchedule();
        return ResponseEntity.ok(schedules);
    }
    
    // Xem lịch theo tuần cụ thể
    @GetMapping("/{weekStart}")
    public ResponseEntity<List<DoctorScheduleDto>> getScheduleByWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        List<DoctorScheduleDto> schedules = hrService.getScheduleByWeek(weekStart);
        return ResponseEntity.ok(schedules);
    }
    
    // Xem lịch tuần tiếp theo
    @GetMapping("/next-week")
    public ResponseEntity<List<DoctorScheduleDto>> getNextWeekSchedule() {
        List<DoctorScheduleDto> schedules = hrService.getNextWeekSchedule();
        return ResponseEntity.ok(schedules);
    }
    
    // Xem lịch theo ngày cụ thể
    @GetMapping("/date/{date}")
    public ResponseEntity<List<DoctorScheduleDto>> getScheduleByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<DoctorScheduleDto> schedules = hrService.getScheduleByDate(date);
        return ResponseEntity.ok(schedules);
    }
    
    // 3. VALIDATION
    @PostMapping("/validate")
    public ResponseEntity<ValidationResultDto> validateSchedule(@Valid @RequestBody CreateWeeklyScheduleRequest request) {
        ValidationResultDto result = hrService.validateSchedule(request);
        return ResponseEntity.ok(result);
    }
    
    // 4. AI GENERATE SCHEDULE
    @PostMapping("/ai/generate")
    public ResponseEntity<CreateWeeklyScheduleRequest> generateScheduleFromDescription(
            @RequestBody Map<String, Object> request) {
        LocalDate weekStart = LocalDate.parse((String) request.get("weekStart"));
        String description = (String) request.get("description");
        CreateWeeklyScheduleRequest generatedRequest = aiScheduleGenerationService.generateScheduleFromDescription(weekStart, description);
        return ResponseEntity.ok(generatedRequest);
    }
    
    // 5. AI GENERATE SCHEDULE TỪ PROMPT TÙY CHỈNH
    @PostMapping("/ai/generate-from-prompt")
    public ResponseEntity<CreateWeeklyScheduleRequest> generateScheduleFromCustomPrompt(
            @RequestBody Map<String, Object> request) {
        LocalDate weekStart = LocalDate.parse((String) request.get("weekStart"));
        String customPrompt = (String) request.get("customPrompt");
        if (customPrompt == null || customPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("customPrompt is required");
        }
        CreateWeeklyScheduleRequest generatedRequest = aiScheduleGenerationService.generateScheduleFromCustomPrompt(weekStart, customPrompt);
        return ResponseEntity.ok(generatedRequest);
    }
    
    // 6. MOCK AI GENERATE (FOR TESTING UI WITHOUT GEMINI API)
    // CHỈ DÙNG TRONG MÔI TRƯỜNG DEVELOPMENT - KHÔNG DÙNG TRONG PRODUCTION
    @org.springframework.context.annotation.Profile("dev")
    @PostMapping("/ai/generate-mock")
    public ResponseEntity<CreateWeeklyScheduleRequest> generateScheduleMock(
            @RequestBody Map<String, Object> request) {
        LocalDate weekStart = LocalDate.parse((String) request.get("weekStart"));
        
        // Tạo mock data để test UI
        CreateWeeklyScheduleRequest mockRequest = new CreateWeeklyScheduleRequest();
        mockRequest.setWeekStart(weekStart);
        mockRequest.setNote("Mock schedule generated for testing UI");
        
        java.util.Map<String, java.util.List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest>> dailyAssignments = new java.util.HashMap<>();
        
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (String day : days) {
            java.util.List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> assignments = new java.util.ArrayList<>();
            
            // Doctor 1 - Morning Clinic 1
            CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment1 = new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
            assignment1.setDoctorId(1);
            assignment1.setClinicId(1);
            assignment1.setRoomId(1);
            assignment1.setStartTime(java.time.LocalTime.of(8, 0));
            assignment1.setEndTime(java.time.LocalTime.of(11, 0));
            assignments.add(assignment1);
            
            // Doctor 1 - Afternoon Clinic 1
            CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment2 = new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
            assignment2.setDoctorId(1);
            assignment2.setClinicId(1);
            assignment2.setRoomId(1);
            assignment2.setStartTime(java.time.LocalTime.of(13, 0));
            assignment2.setEndTime(java.time.LocalTime.of(18, 0));
            assignments.add(assignment2);
            
            // Doctor 2 - Morning Clinic 2
            CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment3 = new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
            assignment3.setDoctorId(2);
            assignment3.setClinicId(2);
            assignment3.setRoomId(2);
            assignment3.setStartTime(java.time.LocalTime.of(8, 0));
            assignment3.setEndTime(java.time.LocalTime.of(11, 0));
            assignments.add(assignment3);
            
            // Doctor 2 - Afternoon Clinic 2
            CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment4 = new CreateWeeklyScheduleRequest.DoctorAssignmentRequest();
            assignment4.setDoctorId(2);
            assignment4.setClinicId(2);
            assignment4.setRoomId(2);
            assignment4.setStartTime(java.time.LocalTime.of(13, 0));
            assignment4.setEndTime(java.time.LocalTime.of(18, 0));
            assignments.add(assignment4);
            
            dailyAssignments.put(day, assignments);
        }
        
        mockRequest.setDailyAssignments(dailyAssignments);
        
        return ResponseEntity.ok(mockRequest);
    }
    
}