package sunshine_dental_care.services.interfaces.hr;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import sunshine_dental_care.entities.DoctorSchedule;

public interface ShiftService {
    String determineShiftForDoctor(LocalTime currentTime);

    void validateCheckInTime(String shiftType, LocalTime currentTime);

    void validateCheckOutTime(String shiftType, LocalTime currentTime);

    Optional<DoctorSchedule> findMatchingSchedule(Integer userId, Integer clinicId, LocalDate date, String shiftType);

    void activateSchedule(DoctorSchedule schedule);

    void activateAfternoonSchedulesIfMorningCheckIn(Integer userId, LocalDate date, String shiftType,
            Integer currentScheduleId);

    LocalTime getExpectedStartTime(String shiftType, DoctorSchedule schedule);

    LocalTime getExpectedEndTime(String shiftType, DoctorSchedule schedule);
}
