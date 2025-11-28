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
    
    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);

    private static final List<String> FORBIDDEN_ROLES = List.of(
            "ADMIN", "Admin", "admin",
            "USER", "User", "user"
    );

    private static final List<String> DOCTOR_ROLE_NAMES = List.of(
            "DOCTOR", "Doctor", "doctor", "BÁC SĨ", "bác sĩ"
    );

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

    // Đánh giá trạng thái chấm công dựa vào giờ check-in và so với giờ ca mặc định
    public String determineAttendanceStatus(Integer userId,
                                            Integer clinicId,
                                            LocalDate workDate,
                                            Instant checkInTime) {
        LocalTime expectedStartTime = LocalTime.of(8, 0);

        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

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

    // Đánh dấu vắng mặt nếu ngày đó không có check-in (cho nhân viên)
    // Nếu có approved leave request → set status APPROVED_ABSENCE, nếu không → ABSENT
    public void markAbsentIfNeeded(Integer userId, Integer clinicId, LocalDate workDate) {
        Optional<Attendance> attendance = attendanceRepository
                .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);

        boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;

        if (!hasCheckIn) {
            // Kiểm tra xem user có approved leave request trong ngày này không
            boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);
            
            Attendance absentRecord = attendance.orElseGet(Attendance::new);
            if (attendance.isEmpty()) {
                absentRecord.setUserId(userId);
                absentRecord.setClinicId(clinicId);
                absentRecord.setWorkDate(workDate);
            }
            
            // Nếu có approved leave request → set APPROVED_ABSENCE, nếu không → ABSENT
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

    // Đánh dấu vắng mặt cho bác sĩ dựa trên schedule (kiểm tra từng ca)
    public void markAbsentForDoctorBasedOnSchedule(Integer userId, LocalDate workDate) {
        // Lấy tất cả schedule của bác sĩ trong ngày
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, workDate);
        
        if (schedules == null || schedules.isEmpty()) {
            log.debug("Doctor {} has no schedule on {}, skipping ABSENT check", userId, workDate);
            return;
        }

        // Với mỗi schedule, check xem có attendance tương ứng không
        for (DoctorSchedule schedule : schedules) {
            if (schedule == null || schedule.getClinic() == null) {
                continue;
            }
            
            Integer clinicId = schedule.getClinic().getId();
            if (clinicId == null) {
                continue;
            }

            // Xác định shiftType dựa trên startTime
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

            // Check xem có attendance cho ca này chưa
            Optional<Attendance> attendance = attendanceRepository
                    .findByUserIdAndClinicIdAndWorkDateAndShiftType(userId, clinicId, workDate, shiftType);

            boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;

            try {
                if (!hasCheckIn) {
                    // Với ca AFTERNOON: Chỉ set INACTIVE nếu đã qua 13:00 (giờ bắt đầu ca chiều)
                    // Với ca MORNING: Set INACTIVE ngay nếu chưa check-in
                    LocalTime currentTime = LocalTime.now();
                    boolean shouldSetInactive = true;
                    
                    if ("AFTERNOON".equals(shiftType)) {
                        // Kiểm tra xem có check-in ca MORNING không (bất kể clinic nào)
                        // Tìm tất cả attendance MORNING của doctor trong ngày
                        List<Attendance> allAttendances = attendanceRepository.findAllByUserIdAndWorkDate(userId, workDate);
                        boolean hasMorningCheckIn = allAttendances.stream()
                                .anyMatch(att -> "MORNING".equals(att.getShiftType()) && att.getCheckInTime() != null);
                        
                        if (hasMorningCheckIn) {
                            // Nếu đã check-in ca MORNING và chưa qua 13:00 → Giữ ACTIVE
                            if (currentTime.isBefore(LocalTime.of(13, 0))) {
                                shouldSetInactive = false;
                                log.debug("Doctor {} has MORNING check-in, keeping AFTERNOON schedule ACTIVE until 13:00",
                                        userId);
                            } else {
                                // Đã qua 13:00 mà chưa check-in ca AFTERNOON → Set INACTIVE
                                log.info("Doctor {} has MORNING check-in but no AFTERNOON check-in after 13:00, setting AFTERNOON schedule INACTIVE",
                                        userId);
                            }
                        } else {
                            // Không có check-in ca MORNING → Set INACTIVE ngay
                            log.info("Doctor {} has no MORNING check-in, setting AFTERNOON schedule INACTIVE",
                                    userId);
                        }
                    }
                    
                    // Tạo record ABSENT cho ca này
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
                        // Nếu đã có record rồi, chỉ cần update status
                        log.debug("Attendance record already exists for doctor {} {} shift at clinic {} on {}, updating status",
                                userId, shiftType, clinicId, workDate);
                        if (attendance.isPresent()) {
                            absentRecord = attendance.get();
                            absentRecord.setAttendanceStatus("ABSENT");
                            attendanceRepository.save(absentRecord);
                        }
                    }

                    // Chuyển schedule sang INACTIVE để booking không hiển thị slot (nếu cần)
                    if (shouldSetInactive) {
                        if (schedule.getStatus() == null || !"INACTIVE".equals(schedule.getStatus())) {
                            schedule.setStatus("INACTIVE");
                            try {
                                doctorScheduleRepo.save(schedule);
                                log.info("Set schedule {} to INACTIVE for doctor {} {} shift at clinic {} on {} (no check-in - blocking booking)",
                                        schedule.getId(), userId, shiftType, clinicId, workDate);
                            } catch (Exception e) {
                                log.warn("Failed to update schedule {} status to INACTIVE: {}", schedule.getId(), e.getMessage());
                            }
                        }
                    } else {
                        // Giữ ACTIVE nếu đã check-in ca MORNING và chưa qua 13:00
                        if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                            schedule.setStatus("ACTIVE");
                            try {
                                doctorScheduleRepo.save(schedule);
                                log.info("Set schedule {} to ACTIVE for doctor {} {} shift at clinic {} on {} (MORNING check-in - default ACTIVE)",
                                        schedule.getId(), userId, shiftType, clinicId, workDate);
                            } catch (Exception e) {
                                log.warn("Failed to update schedule {} status to ACTIVE: {}", schedule.getId(), e.getMessage());
                            }
                        }
                    }

                    log.info("Marked doctor {} as ABSENT for {} shift at clinic {} on {} (has schedule but no check-in)",
                            userId, shiftType, clinicId, workDate);
                } else {
                    // Nếu có check-in, đảm bảo schedule là ACTIVE để booking có thể hiển thị slot
                    if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                        schedule.setStatus("ACTIVE");
                        try {
                            doctorScheduleRepo.save(schedule);
                            log.info("Set schedule {} to ACTIVE for doctor {} {} shift at clinic {} on {} (has check-in - allow booking)",
                                    schedule.getId(), userId, shiftType, clinicId, workDate);
                        } catch (Exception e) {
                            log.warn("Failed to update schedule {} status to ACTIVE: {}", schedule.getId(), e.getMessage());
                        }
                    }
                    
                    // Nếu check-in ca MORNING → Set ACTIVE cho TẤT CẢ schedule AFTERNOON của doctor trong ngày (bất kể clinic)
                    if ("MORNING".equals(shiftType)) {
                        // Lấy TẤT CẢ schedule của doctor trong ngày (không chỉ trong clinic hiện tại)
                        List<DoctorSchedule> allSchedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, workDate);
                        for (DoctorSchedule otherSchedule : allSchedules) {
                            if (otherSchedule != null && !otherSchedule.getId().equals(schedule.getId())) {
                                LocalTime otherStartTime = otherSchedule.getStartTime();
                                if (otherStartTime != null && otherStartTime.isAfter(LUNCH_BREAK_START)) {
                                    // Đây là schedule AFTERNOON
                                    if (otherSchedule.getStatus() == null || !"ACTIVE".equals(otherSchedule.getStatus())) {
                                        otherSchedule.setStatus("ACTIVE");
                                        try {
                                            doctorScheduleRepo.save(otherSchedule);
                                            log.info("Set schedule {} (AFTERNOON) to ACTIVE for doctor {} at clinic {} on {} (MORNING check-in - default ACTIVE)",
                                                    otherSchedule.getId(), userId, 
                                                    otherSchedule.getClinic() != null ? otherSchedule.getClinic().getId() : "unknown", workDate);
                                        } catch (Exception e) {
                                            log.warn("Failed to update schedule {} status to ACTIVE: {}", otherSchedule.getId(), e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    log.debug("Doctor {} has check-in for {} shift at clinic {} on {}, skipping ABSENT check",
                            userId, shiftType, clinicId, workDate);
                }
            } catch (Exception e) {
                // Log lỗi nhưng tiếp tục xử lý các schedule khác
                log.error("Error processing schedule {} for doctor {} on {}: {}", 
                        schedule != null ? schedule.getId() : "null", userId, workDate, e.getMessage(), e);
            }
        }
    }

    // Kiểm tra role người dùng có thuộc nhóm cấm chấm công không
    public boolean isForbiddenRole(UserRole userRole) {
        if (userRole == null || userRole.getRole() == null) {
            return false;
        }
        String roleName = userRole.getRole().getRoleName();
        return roleName != null && FORBIDDEN_ROLES.stream()
                .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
    }

    // Kiểm tra role người dùng có phải là bác sĩ không
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

    // Lấy giờ bắt đầu ca làm việc mặc định (đọc từ config, nếu lỗi trả về 08:00)
    public LocalTime getDefaultStartTime() {
        try {
            return LocalTime.parse(defaultStartTime);
        } catch (Exception e) {
            log.warn("Invalid default start time format: {}. Using 08:00 as fallback.", defaultStartTime);
            return LocalTime.of(8, 0);
        }
    }

    // Reset status schedule về ACTIVE cho ngày mới (gọi khi bắt đầu ngày mới)
    // CHỈ reset những schedule INACTIVE do vắng mặt, KHÔNG reset những schedule INACTIVE do leave request approved
    public void resetScheduleStatusForNewDay(LocalDate workDate) {
        // Lấy tất cả schedule của ngày đó có status INACTIVE
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
            
            // Check xem bác sĩ có leave request approved trong ngày này không
            if (schedule.getDoctor() != null && schedule.getDoctor().getId() != null) {
                boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(
                        schedule.getDoctor().getId(), workDate);
                
                if (hasApprovedLeave) {
                    // KHÔNG reset schedule nếu có leave request approved
                    skippedCount++;
                    log.debug("Skipping reset schedule {} for doctor {} on {} - has approved leave request",
                            schedule.getId(), schedule.getDoctor().getId(), workDate);
                    continue;
                }
            }
            
            // Reset schedule về ACTIVE (chỉ reset những schedule do vắng mặt)
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
