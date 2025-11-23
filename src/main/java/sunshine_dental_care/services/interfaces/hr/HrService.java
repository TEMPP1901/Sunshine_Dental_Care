package sunshine_dental_care.services.interfaces.hr;

import java.time.LocalDate;
import java.util.List;

import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;

public interface HrService {

    // Tạo lịch làm việc mới cho tuần
    List<DoctorScheduleDto> createWeeklySchedule(CreateWeeklyScheduleRequest request);

    // Lấy lịch làm việc tuần hiện tại
    List<DoctorScheduleDto> getCurrentWeekSchedule();

    // Lấy lịch làm việc theo tuần chỉ định (theo ngày bắt đầu tuần)
    List<DoctorScheduleDto> getScheduleByWeek(LocalDate weekStart);

    // Lấy lịch làm việc cho tuần tiếp theo
    List<DoctorScheduleDto> getNextWeekSchedule();

    // Lấy lịch làm việc theo ngày
    List<DoctorScheduleDto> getScheduleByDate(LocalDate date);

    // Kiểm tra hợp lệ của lịch làm việc tạo mới
    ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request);
}
