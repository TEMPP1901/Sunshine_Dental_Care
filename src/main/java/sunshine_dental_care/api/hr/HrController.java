package sunshine_dental_care.api.hr;

import java.time.LocalDate;
import java.util.List;

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
import sunshine_dental_care.services.interfaces.hr.HrService;


@RestController
@RequestMapping("/api/hr/schedules")
@RequiredArgsConstructor
public class HrController {
    
    private final HrService hrService;
    
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
    
}