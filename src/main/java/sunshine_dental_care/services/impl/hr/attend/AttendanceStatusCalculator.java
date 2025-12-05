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
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceStatusCalculator {

    private final UserRoleRepo userRoleRepo;
    private final AttendanceRepository attendanceRepository;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final HolidayService holidayService;

    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);

    private static final List<String> FORBIDDEN_ROLES = List.of(
            "ADMIN", "Admin", "admin",
            "USER", "User", "user");

    private static final List<String> DOCTOR_ROLE_NAMES = List.of(
            "DOCTOR", "Doctor", "doctor", "BÁC SĨ", "bác sĩ");

    @Value("${app.attendance.default-start-time:08:00}")
    private String defaultStartTime;

    // Kiểm tra quyền role người dùng cho phép chấm công hay không
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
                    "Users with ADMIN or USER role are not allowed to check in/out.");
        }

        log.debug("User {} has been validated for attendance", userId);
    }

    // Đánh giá trạng thái chấm công cho nhân viên dựa trên thời gian check-in so
    // với giờ bắt đầu mặc định
    public String determineAttendanceStatus(Integer userId,
            Integer clinicId,
            LocalDate workDate,
            Instant checkInTime) {
        LocalTime expectedStartTime = LocalTime.of(8, 0);
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

        // Kiểm tra xem có đơn nghỉ phép đã duyệt cho ngày này không
        boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);

        if (checkInLocalTime.isBefore(expectedStartTime) || checkInLocalTime.equals(expectedStartTime)) {
            log.info("User {} checked in ON_TIME: {} (expected: {})",
                    userId, checkInLocalTime, expectedStartTime);
            return "ON_TIME";
        } else {
            // Nếu có đơn nghỉ phép đã duyệt, coi như APPROVED_LATE thay vì LATE
            if (hasApprovedLeave) {
                long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                log.info(
                        "User {} checked in APPROVED_LATE with approved leave: {} minutes late (expected: {}, actual: {})",
                        userId, minutesLate, expectedStartTime, checkInLocalTime);
                return "APPROVED_LATE";
            } else {
                long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                log.info("User {} checked in LATE: {} minutes late (expected: {}, actual: {})",
                        userId, minutesLate, expectedStartTime, checkInLocalTime);
                return "LATE";
            }
        }
    }

    // Đánh dấu nhân viên vắng mặt nếu không có check-in và xét trạng thái đơn nghỉ
    // phép
    public void markAbsentIfNeeded(Integer userId, Integer clinicId, LocalDate workDate) {
        Optional<Attendance> attendance = attendanceRepository
                .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);

        boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;

        if (!hasCheckIn) {
            // Check for holiday first
            if (holidayService.isHoliday(workDate, clinicId)) {
                Attendance holidayRecord = attendance.orElseGet(Attendance::new);
                if (attendance.isEmpty()) {
                    holidayRecord.setUserId(userId);
                    holidayRecord.setClinicId(clinicId);
                    holidayRecord.setWorkDate(workDate);
                }
                // Only update if status is not already set or is ABSENT
                if (holidayRecord.getAttendanceStatus() == null
                        || "ABSENT".equals(holidayRecord.getAttendanceStatus())) {
                    holidayRecord.setAttendanceStatus("HOLIDAY");
                    attendanceRepository.save(holidayRecord);
                    log.info("Marked user {} as HOLIDAY at clinic {} on {}", userId, clinicId, workDate);
                }
                return;
            }

            boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);

            Attendance absentRecord = attendance.orElseGet(Attendance::new);
            if (attendance.isEmpty()) {
                absentRecord.setUserId(userId);
                absentRecord.setClinicId(clinicId);
                absentRecord.setWorkDate(workDate);
            }

            String status = hasApprovedLeave ? "APPROVED_ABSENCE" : "ABSENT";
            absentRecord.setAttendanceStatus(status);
            attendanceRepository.save(absentRecord);

            log.info("Marked user {} as {} at clinic {} on {} (no check-in found. Has approved leave: {})",
                    userId, status, clinicId, workDate, hasApprovedLeave);
        } else {
            log.debug("User {} has check-in at clinic {} on {}, skipping ABSENT check",
                    userId, clinicId, workDate);
        }
    }

    // Đánh dấu bác sĩ vắng mặt dựa vào từng schedule và trạng thái check-in của
    // từng ca
    public void markAbsentForDoctorBasedOnSchedule(Integer userId, LocalDate workDate) {
        // Nếu đã có đơn nghỉ phép đã duyệt thì không xét vắng mặt để tránh reset lịch
        // INACTIVE
        if (leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate)) {
            log.debug("Doctor {} has approved leave on {}, skipping attendance check", userId, workDate);
            return;
        }

        List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, workDate);

        if (schedules == null || schedules.isEmpty()) {
            log.debug("Doctor {} has no schedule on {}, skipping ABSENT check", userId, workDate);
            return;
        }

        for (DoctorSchedule schedule : schedules) {
            if (schedule == null || schedule.getClinic() == null) {
                continue;
            }

            Integer clinicId = schedule.getClinic().getId();
            if (clinicId == null) {
                continue;
            }

            // Check for holiday
            if (holidayService.isHoliday(workDate, clinicId)) {
                log.debug("It is holiday at clinic {}, skipping absent check for doctor {}", clinicId, userId);
                continue;
            }

            String shiftType;
            LocalTime startTime = schedule.getStartTime();
            if (startTime == null) {
                continue;
            }

            if (startTime.isBefore(LUNCH_BREAK_START)) {
                shiftType = "MORNING";
            } else {
                shiftType = "AFTERNOON";
            }

            Optional<Attendance> attendance = attendanceRepository
                    .findByUserIdAndClinicIdAndWorkDateAndShiftType(userId, clinicId, workDate, shiftType);

            boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;

            try {
                if (!hasCheckIn) {
                    LocalTime currentTime = LocalTime.now();
                    // Nếu quá 2 tiếng kể từ giờ ca bắt đầu mà chưa check-in thì sẽ set schedule:
                    // INACTIVE và đánh dấu ABSENT
                    LocalTime inactiveThreshold = startTime.plusHours(2);

                    boolean shouldSetInactive = currentTime.isAfter(inactiveThreshold);

                    if (shouldSetInactive) {
                        Attendance absentRecord = attendance.orElseGet(Attendance::new);
                        if (attendance.isEmpty()) {
                            absentRecord.setUserId(userId);
                            absentRecord.setClinicId(clinicId);
                            absentRecord.setWorkDate(workDate);
                            absentRecord.setShiftType(shiftType);
                        }
                        absentRecord.setAttendanceStatus("ABSENT");
                        try {
                            attendanceRepository.save(absentRecord);
                        } catch (org.springframework.dao.DataIntegrityViolationException e) {
                            if (attendance.isPresent()) {
                                absentRecord = attendance.get();
                                absentRecord.setAttendanceStatus("ABSENT");
                                attendanceRepository.save(absentRecord);
                            }
                        }

                        if (schedule.getStatus() == null || !"INACTIVE".equals(schedule.getStatus())) {
                            schedule.setStatus("INACTIVE");
                            try {
                                doctorScheduleRepo.save(schedule);
                                log.info(
                                        "Set schedule {} to INACTIVE for doctor {} {} shift at clinic {} on {} (No check-in after 2 hours)",
                                        schedule.getId(), userId, shiftType, clinicId, workDate);
                            } catch (Exception e) {
                                log.warn("Failed to update schedule {} status to INACTIVE: {}", schedule.getId(),
                                        e.getMessage());
                            }
                        }
                    } else {
                        // Nếu vẫn còn trong khoảng 2 giờ sau giờ bắt đầu ca thì giữ schedule status là
                        // ACTIVE
                        if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                            schedule.setStatus("ACTIVE");
                            try {
                                doctorScheduleRepo.save(schedule);
                                log.info(
                                        "Set schedule {} to ACTIVE for doctor {} {} shift at clinic {} on {} (Within 2-hour grace period)",
                                        schedule.getId(), userId, shiftType, clinicId, workDate);
                            } catch (Exception e) {
                                log.warn("Failed to update schedule {} status to ACTIVE: {}", schedule.getId(),
                                        e.getMessage());
                            }
                        }
                        log.debug(
                                "Doctor {} has no check-in for {} shift but within grace period (until {}), keeping ACTIVE",
                                userId, shiftType, inactiveThreshold);
                    }
                } else {
                    // Nếu bác sĩ đã check-in thì phải đảm bảo schedule là ACTIVE
                    if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                        schedule.setStatus("ACTIVE");
                        try {
                            doctorScheduleRepo.save(schedule);
                            log.info(
                                    "Set schedule {} to ACTIVE for doctor {} {} shift at clinic {} on {} (Has check-in)",
                                    schedule.getId(), userId, shiftType, clinicId, workDate);
                        } catch (Exception e) {
                            log.warn("Failed to update schedule {} status to ACTIVE: {}", schedule.getId(),
                                    e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing schedule {} for doctor {} on {}: {}",
                        schedule != null ? schedule.getId() : "null", userId, workDate, e.getMessage(), e);
            }
        }
    }

    // Kiểm tra role của user có nằm trong danh sách cấm chấm công không
    public boolean isForbiddenRole(UserRole userRole) {
        if (userRole == null || userRole.getRole() == null) {
            return false;
        }
        String roleName = userRole.getRole().getRoleName();
        return roleName != null && FORBIDDEN_ROLES.stream()
                .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
    }

    // Kiểm tra role của user có phải bác sĩ không
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

    // Lấy giờ bắt đầu ca mặc định (config), nếu lỗi thì trả 08:00
    public LocalTime getDefaultStartTime() {
        try {
            return LocalTime.parse(defaultStartTime);
        } catch (Exception e) {
            log.warn("Invalid default start time format: {}. Using 08:00 as fallback.", defaultStartTime);
            return LocalTime.of(8, 0);
        }
    }

    // Kiểm tra user có đơn nghỉ phép đã duyệt cho ngày cụ thể không
    public boolean hasApprovedLeaveOnDate(Integer userId, LocalDate workDate) {
        return leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);
    }

    // Kiểm tra user có đơn nghỉ phép đã duyệt cho ngày và ca cụ thể không (cho bác
    // sĩ)
    public boolean hasApprovedLeaveOnDateAndShift(Integer userId, LocalDate workDate, String shiftType) {
        if (shiftType == null || shiftType.isEmpty() || "FULL_DAY".equals(shiftType)) {
            // Nếu FULL_DAY hoặc null, check như bình thường
            return leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);
        }
        // Nếu có shiftType cụ thể (MORNING/AFTERNOON), check theo ca
        return leaveRequestRepo.hasApprovedLeaveOnDateAndShift(userId, workDate, shiftType);
    }

    // Reset trạng thái INACTIVE schedule về ACTIVE cho ngày mới (chỉ reset do vắng
    // mặt, không reset nếu đã approved nghỉ phép)
    public void resetScheduleStatusForNewDay(LocalDate workDate) {
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWorkDate(workDate);

        if (schedules == null || schedules.isEmpty()) {
            log.debug("No schedules found for date {}, skipping status reset", workDate);
            return;
        }

        int resetCount = 0;
        int skippedCount = 0;

        for (DoctorSchedule schedule : schedules) {
            if (schedule == null || !"INACTIVE".equals(schedule.getStatus())) {
                continue;
            }

            // Không reset schedule nếu bác sĩ có đơn nghỉ phép đã duyệt trong ngày này
            if (schedule.getDoctor() != null && schedule.getDoctor().getId() != null) {
                boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(
                        schedule.getDoctor().getId(), workDate);

                if (hasApprovedLeave) {
                    skippedCount++;
                    log.debug("Skipping reset schedule {} for doctor {} on {} - has approved leave request",
                            schedule.getId(), schedule.getDoctor().getId(), workDate);
                    continue;
                }
            }

            schedule.setStatus("ACTIVE");
            doctorScheduleRepo.save(schedule);
            resetCount++;
        }

        if (resetCount > 0) {
            log.info("Reset {} schedules to ACTIVE for date {} (skipped {} due to approved leave requests)",
                    resetCount, workDate, skippedCount);
        } else if (skippedCount > 0) {
            log.debug("No schedules reset for date {} - all INACTIVE schedules have approved leave requests",
                    workDate);
        } else {
            log.debug("No INACTIVE schedules found for date {}, no reset needed", workDate);
        }
    }
}
