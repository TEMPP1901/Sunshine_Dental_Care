package sunshine_dental_care.services.impl.hr.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.services.interfaces.hr.ShiftService;
import sunshine_dental_care.utils.WorkHoursConstants;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftServiceImpl implements ShiftService {

    private final DoctorScheduleRepo doctorScheduleRepo;

    // xác định ca làm việc dựa vào giờ vào ca
    @Override
    public String determineShiftForDoctor(LocalTime currentTime) {
        return WorkHoursConstants.determineShiftForDoctor(currentTime);
    }

    // chỉ ca CHIỀU thì không thể check-in trước 13h
    @Override
    public void validateCheckInTime(String shiftType, LocalTime currentTime) {
        if (WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType) 
                && currentTime.isBefore(WorkHoursConstants.AFTERNOON_SHIFT_START)) {
            throw new AttendanceValidationException(
                String.format("Không thể check-in ca CHIỀU trước %s. Giờ hiện tại: %s", 
                    WorkHoursConstants.AFTERNOON_SHIFT_START, currentTime));
        }
    }

    // ca SÁNG không check-out trước 8h, ca CHIỀU không check-out trước 13h
    @Override
    public void validateCheckOutTime(String shiftType, LocalTime currentTime) {
        if (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType)) {
            if (currentTime.isBefore(WorkHoursConstants.MORNING_SHIFT_START)) {
                throw new AttendanceValidationException(
                    String.format("Không thể check-out ca SÁNG trước %s. Giờ hiện tại: %s", 
                        WorkHoursConstants.MORNING_SHIFT_START, currentTime));
            }
        } else if (WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
            if (currentTime.isBefore(WorkHoursConstants.AFTERNOON_SHIFT_START)) {
                throw new AttendanceValidationException(
                    String.format("Không thể check-out ca CHIỀU trước %s. Giờ hiện tại: %s", 
                        WorkHoursConstants.AFTERNOON_SHIFT_START, currentTime));
            }
        }
    }

    // tìm lịch phù hợp nhất với user, date, clinic và ca
    @Override
    public Optional<DoctorSchedule> findMatchingSchedule(Integer userId, Integer clinicId, LocalDate date, String shiftType) {
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDateAllStatus(
            userId, clinicId, date);
        if (schedules.isEmpty()) {
            return Optional.empty();
        }
        for (DoctorSchedule schedule : schedules) {
            LocalTime startTime = schedule.getStartTime();
            // Sử dụng helper method từ WorkHoursConstants để xử lý đúng boundary cases
            if (WorkHoursConstants.matchesShiftType(startTime, shiftType)) {
                return Optional.of(schedule);
            }
        }
        // nếu không đúng ca, trả về lịch có giờ bắt đầu sớm nhất
        return schedules.stream()
                .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()));
    }

    // active schedule nếu chưa ACTIVE
    @Override
    @Transactional
    public void activateSchedule(DoctorSchedule schedule) {
        if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
            schedule.setStatus("ACTIVE");
            doctorScheduleRepo.save(schedule);
        }
    }

    // nếu check-in ca SÁNG thì active toàn bộ ca CHIỀU cùng ngày, ngoại trừ lịch đang check-in
    @Override
    @Transactional
    public void activateAfternoonSchedulesIfMorningCheckIn(Integer userId, LocalDate date, String shiftType, Integer currentScheduleId) {
        if (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType)) {
            var allSchedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, date);
            for (var schedule : allSchedules) {
                if (schedule == null || schedule.getId().equals(currentScheduleId))
                    continue;
                LocalTime scheduleStart = schedule.getStartTime();
                // Active các schedule có startTime >= 11:00 (ca chiều)
                if (scheduleStart == null || scheduleStart.isBefore(WorkHoursConstants.LUNCH_BREAK_START))
                    continue;
                if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                    schedule.setStatus("ACTIVE");
                    doctorScheduleRepo.save(schedule);
                }
            }
        }
    }

    // thời gian bắt đầu kỳ vọng dựa vào loại ca hoặc entity
    @Override
    public LocalTime getExpectedStartTime(String shiftType, DoctorSchedule schedule) {
        if (schedule != null) {
            return schedule.getStartTime();
        }
        if (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType)) {
            return WorkHoursConstants.MORNING_SHIFT_START;
        } else if (WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
            return WorkHoursConstants.AFTERNOON_SHIFT_START;
        } else {
            return WorkHoursConstants.EMPLOYEE_START_TIME;
        }
    }

    // thời gian kết thúc kỳ vọng dựa vào loại ca hoặc entity
    @Override
    public LocalTime getExpectedEndTime(String shiftType, DoctorSchedule schedule) {
        if (schedule != null) {
            return schedule.getEndTime();
        }
        if (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType)) {
            return WorkHoursConstants.MORNING_SHIFT_END;
        } else if (WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
            return WorkHoursConstants.AFTERNOON_SHIFT_END;
        } else {
            return WorkHoursConstants.EMPLOYEE_END_TIME;
        }
    }
}
