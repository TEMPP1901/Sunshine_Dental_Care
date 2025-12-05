package sunshine_dental_care.services.impl.hr.attend;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AlreadyCheckedInException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.hr.AttendanceRepository;

@Component
@RequiredArgsConstructor
public class AttendanceValidationHelper {

    private final AttendanceRepository attendanceRepo;

    // Chặn chấm công vào Chủ nhật
    public void validateCheckInAllowed(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new AttendanceValidationException("Attendance is not allowed on Sundays");
        }
    }

    // Đảm bảo bác sĩ chỉ được check-in 1 lần mỗi ca tại mỗi phòng khám
    public void validateUniqueCheckInForDoctor(Integer userId, Integer clinicId, LocalDate date, String shiftType) {
        Optional<Attendance> existingShift = attendanceRepo
                .findByUserIdAndClinicIdAndWorkDateAndShiftType(userId, clinicId, date, shiftType);
        if (existingShift.isPresent() && existingShift.get().getCheckInTime() != null) {
            throw new AlreadyCheckedInException(
                    String.format("Doctor %d already checked in for %s shift at clinic %d on %s",
                            userId, shiftType, clinicId, date));
        }
    }

    // Đảm bảo nhân viên thường chỉ được check-in 1 lần mỗi ngày
    public void validateUniqueCheckInForEmployee(Integer userId, Integer clinicId, LocalDate date) {
        Optional<Attendance> existing = attendanceRepo.findByUserIdAndWorkDate(userId, date);
        if (existing.isPresent()) {
            Attendance existingAtt = existing.get();
            if (existingAtt.getCheckInTime() != null) {
                throw new AlreadyCheckedInException(
                        String.format("Employee %d already checked in at clinic %d on %s",
                                userId, existingAtt.getClinicId(), date));
            }
        }
    }

    // Validate trạng thái trước khi cho phép check-out
    public void validateCheckOutAllowed(Attendance attendance) {
        if (attendance.getCheckInTime() == null) {
            throw new AttendanceValidationException("Cannot check out without check-in");
        }
        if (attendance.getCheckOutTime() != null) {
            throw new AttendanceValidationException("Already checked out");
        }
    }

    // Chỉ cho phép check-out sau giờ nghỉ trưa (sau 13h)
    public void validateCheckOutTimeForEmployee(LocalTime currentTime) {
        if (currentTime.isBefore(LocalTime.of(13, 0))) {
            throw new AttendanceValidationException(
                    String.format("Check-out is allowed only after lunch break (after 13:00). Current time: %s",
                            currentTime));
        }
    }
}
