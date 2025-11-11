package sunshine_dental_care.services.interfaces.hr;

import java.time.LocalDate;
import java.util.List;

import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;

public interface HrService {
    
    // 1. TẠO LỊCH MỚI cho tuần
    List<DoctorScheduleDto> createWeeklySchedule(CreateWeeklyScheduleRequest request);
    
    // 2. XEM LỊCH
    List<DoctorScheduleDto> getCurrentWeekSchedule();
    List<DoctorScheduleDto> getScheduleByWeek(LocalDate weekStart);
    List<DoctorScheduleDto> getNextWeekSchedule();
    List<DoctorScheduleDto> getScheduleByDate(LocalDate date);
    
    // 3. VALIDATION
    ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request);
}
