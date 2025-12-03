package sunshine_dental_care.services.interfaces.hr;

import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;

public interface ScheduleValidationService {
    // Kiểm tra hợp lệ cho lịch làm việc mới
    ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request);
}
