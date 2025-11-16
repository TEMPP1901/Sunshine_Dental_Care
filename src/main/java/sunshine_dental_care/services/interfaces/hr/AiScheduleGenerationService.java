package sunshine_dental_care.services.interfaces.hr;

import java.time.LocalDate;

import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;

public interface AiScheduleGenerationService {

    // Hàm này sẽ tạo lịch làm việc hàng tuần dựa trên mô tả bằng ngôn ngữ tự nhiên (AI)
    CreateWeeklyScheduleRequest generateScheduleFromDescription(LocalDate weekStart, String description);
    
    // Hàm này sẽ tạo lịch làm việc hàng tuần từ prompt tùy chỉnh của người dùng
    CreateWeeklyScheduleRequest generateScheduleFromCustomPrompt(LocalDate weekStart, String customPrompt);
}
