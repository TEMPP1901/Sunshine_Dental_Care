package sunshine_dental_care.services.interfaces.hr;

import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;

public interface ScheduleValidationService {
    ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request);
}

