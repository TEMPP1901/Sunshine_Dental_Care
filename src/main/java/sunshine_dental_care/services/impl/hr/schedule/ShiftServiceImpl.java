package sunshine_dental_care.services.impl.hr;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class ShiftServiceImpl implements ShiftService {

    private final DoctorScheduleRepo doctorScheduleRepo;

    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_SHIFT_START = LocalTime.of(13, 0);
    private static final LocalTime EMPLOYEE_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime EMPLOYEE_END_TIME = LocalTime.of(18, 0);

    // Xác định ca làm việc (sáng/chiều) cho bác sĩ dựa vào thời gian hiện tại
    @Override
    public String determineShiftForDoctor(LocalTime currentTime) {
        if (currentTime.isBefore(LUNCH_BREAK_START)) {
            return "MORNING";
        } else if (currentTime.isAfter(LUNCH_BREAK_START) && currentTime.isBefore(LocalTime.of(18, 0))) {
            return "AFTERNOON";
        }
        return "AFTERNOON";
    }

    // Kiểm tra hợp lệ khi check-in đầu ca (ca chiều không thể check-in trước 13h00)
    @Override
    public void validateCheckInTime(String shiftType, LocalTime currentTime) {
        if ("AFTERNOON".equals(shiftType) && currentTime.isBefore(AFTERNOON_SHIFT_START)) {
            throw new AttendanceValidationException(
                    String.format("Không thể check-in ca CHIỀU trước 13:00. Giờ hiện tại: %s",
                            currentTime));
        }
    }

    // Kiểm tra hợp lệ khi check-out trong ca sáng và chiều
    @Override
    public void validateCheckOutTime(String shiftType, LocalTime currentTime) {
        if ("MORNING".equals(shiftType)) {
            if (currentTime.isBefore(LocalTime.of(8, 0))) {
                throw new AttendanceValidationException(
                        String.format("Không thể check-out ca SÁNG trước 8:00. Giờ hiện tại: %s",
                                currentTime));
            }
        } else if ("AFTERNOON".equals(shiftType)) {
            if (currentTime.isBefore(AFTERNOON_SHIFT_START)) {
                throw new AttendanceValidationException(
                        String.format("Không thể check-out ca CHIỀU trước 13:00. Giờ hiện tại: %s",
                                currentTime));
            }
        }
    }

    // Tìm schedule phù hợp cho user theo ca, date, clinicId
    @Override
    public Optional<DoctorSchedule> findMatchingSchedule(Integer userId, Integer clinicId, LocalDate date,
            String shiftType) {
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDateAllStatus(
                userId, clinicId, date);

        if (schedules.isEmpty()) {
            return Optional.empty();
        }

        for (DoctorSchedule schedule : schedules) {
            LocalTime startTime = schedule.getStartTime();
            boolean isMorningMatch = "MORNING".equals(shiftType) && startTime.isBefore(LUNCH_BREAK_START);
            boolean isAfternoonMatch = "AFTERNOON".equals(shiftType) && startTime.isAfter(LUNCH_BREAK_START);
            if (isMorningMatch || isAfternoonMatch) {
                return Optional.of(schedule);
            }
        }

        // Nếu không tìm được, trả về lịch sớm nhất
        return schedules.stream()
                .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()));
    }

    // Kích hoạt lịch làm việc (status = ACTIVE) nếu chưa active
    @Override
    @Transactional
    public void activateSchedule(DoctorSchedule schedule) {
        if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
            schedule.setStatus("ACTIVE");
            doctorScheduleRepo.save(schedule);
        }
    }

    // Khi check-in ca sáng, kích hoạt toàn bộ ca chiều trong ngày tương ứng
    @Override
    @Transactional
    public void activateAfternoonSchedulesIfMorningCheckIn(Integer userId, LocalDate date, String shiftType,
            Integer currentScheduleId) {
        if ("MORNING".equals(shiftType)) {
            var allSchedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, date);
            for (var schedule : allSchedules) {
                if (schedule == null || schedule.getId().equals(currentScheduleId))
                    continue;
                LocalTime scheduleStart = schedule.getStartTime();
                if (scheduleStart == null || !scheduleStart.isAfter(LUNCH_BREAK_START))
                    continue;
                if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                    schedule.setStatus("ACTIVE");
                    doctorScheduleRepo.save(schedule);
                }
            }
        }
    }

    // Lấy thời gian bắt đầu theo ca hoặc theo entity schedule nếu có
    @Override
    public LocalTime getExpectedStartTime(String shiftType, DoctorSchedule schedule) {
        if (schedule != null) {
            return schedule.getStartTime();
        }

        if ("MORNING".equals(shiftType)) {
            return LocalTime.of(8, 0);
        } else if ("AFTERNOON".equals(shiftType)) {
            return LocalTime.of(13, 0);
        } else {
            return EMPLOYEE_START_TIME;
        }
    }

    // Lấy thời gian kết thúc theo ca hoặc theo entity schedule nếu có
    @Override
    public LocalTime getExpectedEndTime(String shiftType, DoctorSchedule schedule) {
        if (schedule != null) {
            return schedule.getEndTime();
        }

        if ("MORNING".equals(shiftType)) {
            return LocalTime.of(11, 0);
        } else if ("AFTERNOON".equals(shiftType)) {
            return LocalTime.of(18, 0);
        } else {
            return EMPLOYEE_END_TIME;
        }
    }
}
