package sunshine_dental_care.services.impl.hr;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceStatusCalculator {

    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AttendanceRepository attendanceRepository;

    private static final List<String> FORBIDDEN_ROLES = List.of(
            "ADMIN", "Admin", "admin",
            "PATIENT", "Patient", "patient"
    );

    private static final List<String> DOCTOR_ROLE_NAMES = List.of(
            "DOCTOR", "Doctor", "doctor", "BÁC SĨ", "bác sĩ"
    );

    @Value("${app.attendance.default-start-time:08:00}")
    private String defaultStartTime;

    public void validateUserRoleForAttendance(Integer userId) {
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);

        if (userRoles == null || userRoles.isEmpty()) {
            throw new AttendanceValidationException(
                    "User does not have any active role. Cannot check in/out.");
        }

        boolean hasForbiddenRole = userRoles.stream()
                .anyMatch(this::isForbiddenRole);

        if (hasForbiddenRole) {
            throw new AttendanceValidationException(
                    "Users with ADMIN or PATIENT role are not allowed to check in/out.");
        }

        log.debug("User {} role validated for attendance", userId);
    }

    public String determineAttendanceStatus(Integer userId,
                                            Integer clinicId,
                                            LocalDate workDate,
                                            Instant checkInTime) {
        // Giờ chuẩn để đánh giá ON_TIME/LATE: 8h (08:00)
        LocalTime expectedStartTime = LocalTime.of(8, 0);
        
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

        // Đánh giá: check-in trước hoặc đúng 8h = ON_TIME, sau 8h = LATE
        if (checkInLocalTime.isBefore(expectedStartTime) || checkInLocalTime.equals(expectedStartTime)) {
            log.info("User {} checked in ON_TIME: {} (expected: {})",
                    userId, checkInLocalTime, expectedStartTime);
            return "ON_TIME";
        } else {
            long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
            log.info("User {} checked in LATE: {} minutes late (expected: {}, actual: {})",
                    userId, minutesLate, expectedStartTime, checkInLocalTime);
            return "LATE";
        }
    }

    /**
     * Đánh giá ABSENT: Khi workDate không có cả check-in và check-out
     * Nếu chỉ có check-out mà không có check-in → vẫn là ABSENT
     */
    public void markAbsentIfNeeded(Integer userId, Integer clinicId, LocalDate workDate) {
        Optional<Attendance> attendance = attendanceRepository
                .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);

        // Kiểm tra: không có check-in HOẶC không có cả check-in và check-out
        boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;
        boolean hasCheckOut = attendance.isPresent() && attendance.get().getCheckOutTime() != null;
        
        // ABSENT nếu: không có check-in (dù có check-out hay không)
        if (!hasCheckIn) {
            Attendance absentRecord = attendance.orElseGet(Attendance::new);
            if (attendance.isEmpty()) {
                absentRecord.setUserId(userId);
                absentRecord.setClinicId(clinicId);
                absentRecord.setWorkDate(workDate);
            }
            absentRecord.setAttendanceStatus("ABSENT");
            attendanceRepository.save(absentRecord);

            log.info("Marked user {} as ABSENT at clinic {} on {} (no check-in found. check-out: {})",
                    userId, clinicId, workDate, hasCheckOut);
        } else {
            log.debug("User {} has check-in at clinic {} on {}, skipping ABSENT check", 
                    userId, clinicId, workDate);
        }
    }

    public boolean isForbiddenRole(UserRole userRole) {
        if (userRole == null || userRole.getRole() == null) {
            return false;
        }
        String roleName = userRole.getRole().getRoleName();
        return roleName != null && FORBIDDEN_ROLES.stream()
                .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
    }

    public boolean isDoctorRole(UserRole userRole) {
        if (userRole == null || userRole.getRole() == null) {
            return false;
        }
        String roleName = userRole.getRole().getRoleName();
        return roleName != null && DOCTOR_ROLE_NAMES.stream()
                .anyMatch(docRole -> roleName.equalsIgnoreCase(docRole));
    }

    public boolean hasForbiddenRole(List<UserRole> roles) {
        return roles != null && roles.stream().anyMatch(this::isForbiddenRole);
    }

    public LocalTime getDefaultStartTime() {
        try {
            return LocalTime.parse(defaultStartTime);
        } catch (Exception e) {
            log.warn("Invalid default start time format: {}. Using 08:00 as fallback.", defaultStartTime);
            return LocalTime.of(8, 0);
        }
    }
}


