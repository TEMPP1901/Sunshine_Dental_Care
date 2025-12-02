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

    // Kiểm tra quyền của user cho phép chấm công hay không
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

    // Đánh giá trạng thái chấm công dựa vào thời gian check-in
    public String determineAttendanceStatus(Integer userId,
                                            Integer clinicId,
                                            LocalDate workDate,
                                            Instant checkInTime) {
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();
        LocalTime expectedStartTime = LocalTime.of(8, 0); // Mặc định 8:00
        
        // Kiểm tra xem user có phải bác sĩ không
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles.stream().anyMatch(this::isDoctorRole);
        
        if (isDoctor) {
            // Bác sĩ: Lấy giờ bắt đầu từ lịch phân công
            var schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
            if (!schedules.isEmpty()) {
                // Lấy ca đầu tiên trong ngày
                var firstSchedule = schedules.stream()
                        .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()))
                        .orElse(null);
                if (firstSchedule != null) {
                    expectedStartTime = firstSchedule.getStartTime();
                    log.debug("Doctor {} expected start time from schedule: {}", userId, expectedStartTime);
                }
            } else {
                log.warn("Doctor {} has no schedule on {} at clinic {}. Using default 8:00 for status calculation.",
                        userId, workDate, clinicId);
            }
        }

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

    // Đánh dấu vắng mặt nếu không có check-in
    public void markAbsentIfNeeded(Integer userId, Integer clinicId, LocalDate workDate) {
        Optional<Attendance> attendance = attendanceRepository
                .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);

        boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;

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
                    userId, clinicId, workDate,
                    attendance.isPresent() && attendance.get().getCheckOutTime() != null);
        } else {
            log.debug("User {} has check-in at clinic {} on {}, skipping ABSENT check",
                    userId, clinicId, workDate);
        }
    }

    // Kiểm tra role bị cấm chấm công
    public boolean isForbiddenRole(UserRole userRole) {
        if (userRole == null || userRole.getRole() == null) {
            return false;
        }
        String roleName = userRole.getRole().getRoleName();
        return roleName != null && FORBIDDEN_ROLES.stream()
                .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
    }

    // Kiểm tra role có phải bác sĩ không
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

    // Lấy giờ bắt đầu ca làm mặc định
    public LocalTime getDefaultStartTime() {
        try {
            return LocalTime.parse(defaultStartTime);
        } catch (Exception e) {
            log.warn("Invalid default start time format: {}. Using 08:00 as fallback.", defaultStartTime);
            return LocalTime.of(8, 0);
        }
    }
}
