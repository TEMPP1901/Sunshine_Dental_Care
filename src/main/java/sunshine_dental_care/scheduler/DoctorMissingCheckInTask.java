package sunshine_dental_care.scheduler;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.repositories.NotificationRepository;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.services.impl.hr.schedule.HolidayService;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.utils.WorkHoursConstants;

@Component
@RequiredArgsConstructor
@Slf4j
public class DoctorMissingCheckInTask {

    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AttendanceRepository attendanceRepo;
    private final UserRoleRepo userRoleRepo;
    private final LeaveRequestRepo leaveRequestRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final HolidayService holidayService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    // Hàm kiểm tra bác sĩ chưa check-in sau 30 phút từ giờ bắt đầu ca, chạy mỗi 10 phút
    @Scheduled(cron = "0 */10 * * * ?")
    @Transactional
    public void checkDoctorsMissingCheckIn() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        
        // Lấy tất cả lịch làm việc hôm nay
        List<DoctorSchedule> todaySchedules = doctorScheduleRepo.findByWorkDate(today);
        
        if (todaySchedules.isEmpty()) {
            log.debug("No doctor schedules found for today");
            return;
        }

        int checkedCount = 0;
        int notifiedCount = 0;

        for (DoctorSchedule schedule : todaySchedules) {
            try {
                // Chỉ xử lý những ca làm việc đang ACTIVE
                if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                    continue;
                }

                // Bỏ qua nếu hôm nay là ngày nghỉ của phòng khám
                if (schedule.getClinic() != null && schedule.getClinic().getId() != null) {
                    if (holidayService.isHoliday(today, schedule.getClinic().getId())) {
                        continue;
                    }
                }

                // Bỏ qua nếu bác sĩ có đơn nghỉ phép đã duyệt cho ca này
                if (schedule.getDoctor() != null && schedule.getDoctor().getId() != null) {
                    String shiftType = WorkHoursConstants.determineShiftType(schedule.getStartTime());
                    if (leaveRequestRepo.hasApprovedLeaveOnDateAndShift(
                            schedule.getDoctor().getId(), today, shiftType)) {
                        continue;
                    }
                }

                LocalTime startTime = schedule.getStartTime();
                if (startTime == null) {
                    continue;
                }

                long minutesPassed = java.time.Duration.between(startTime, now).toMinutes();

                // Chỉ kiểm tra nếu đã qua 30 phút từ đầu ca
                if (minutesPassed < 30) {
                    continue;
                }

                // Kiểm tra thông tin bác sĩ và phòng khám
                if (schedule.getDoctor() == null || schedule.getDoctor().getId() == null 
                        || schedule.getClinic() == null || schedule.getClinic().getId() == null) {
                    continue;
                }

                Integer doctorId = schedule.getDoctor().getId();
                Integer clinicId = schedule.getClinic().getId();
                String shiftType = WorkHoursConstants.determineShiftType(startTime);

                // Kiểm tra xem bác sĩ đã check-in chưa
                Optional<sunshine_dental_care.entities.Attendance> attendance = attendanceRepo
                        .findByUserIdAndClinicIdAndWorkDateAndShiftType(doctorId, clinicId, today, shiftType);

                boolean hasCheckIn = attendance.isPresent() && attendance.get().getCheckInTime() != null;

                if (!hasCheckIn) {
                    checkedCount++;
                    sendMissingCheckInNotification(schedule, (int) minutesPassed);   // Gửi thông báo cho lễ tân
                    notifiedCount++;
                }
            } catch (Exception e) {
                log.error("Error checking schedule {} for missing check-in: {}", 
                        schedule.getId(), e.getMessage(), e);
            }
        }

        if (checkedCount > 0) {
            log.info("Checked {} schedules, sent {} notifications for doctors missing check-in after 30 minutes", 
                    checkedCount, notifiedCount);
        }
    }

    // Gửi thông báo cho lễ tân khi bác sĩ chưa check-in đúng hạn
    // Chỉ gửi một lần theo ca (kiểm tra xem đã gửi thông báo cho schedule này trong ngày hôm nay chưa)
    private void sendMissingCheckInNotification(DoctorSchedule schedule, int minutesPassed) {
        try {
            if (schedule.getDoctor() == null || schedule.getClinic() == null) {
                return;
            }

            String doctorName = schedule.getDoctor().getFullName();
            String clinicName = schedule.getClinic().getClinicName();
            Integer clinicId = schedule.getClinic().getId();
            LocalTime startTime = schedule.getStartTime();
            String startTimeStr = startTime != null ? startTime.toString().substring(0, 5) : "N/A";

            // Lấy danh sách reception users của cơ sở này
            List<Integer> receptionUserIds = getReceptionUserIdsByClinic(clinicId);
            if (receptionUserIds == null || receptionUserIds.isEmpty()) {
                log.warn("No RECEPTION users found for clinic {} to notify about missing check-in", clinicId);
                return;
            }

            String message = String.format(
                    "Bác sĩ %s chưa check-in sau %d phút từ giờ bắt đầu ca (%s) tại %s",
                    doctorName, minutesPassed, startTimeStr, clinicName);

            int successCount = 0;
            int skippedCount = 0;
            
            for (Integer receptionUserId : receptionUserIds) {
                try {
                    // Kiểm tra xem đã gửi thông báo cho schedule này và user này trong ngày hôm nay chưa
                    long existingNotificationCount = notificationRepository.countByUserIdAndTypeAndEntityToday(
                            receptionUserId,
                            "DOCTOR_MISSING_CHECKIN",
                            "DOCTOR_SCHEDULE",
                            schedule.getId()
                    );
                    
                    if (existingNotificationCount > 0) {
                        // Đã gửi thông báo rồi, bỏ qua
                        skippedCount++;
                        log.debug("Skipping notification for reception user {} - already notified about schedule {} today", 
                                receptionUserId, schedule.getId());
                        continue;
                    }
                    
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(receptionUserId)
                            .type("DOCTOR_MISSING_CHECKIN")
                            .priority("MEDIUM")
                            .title("Bác sĩ chưa check-in")
                            .message(message)
                            .actionUrl("/hr/attendance")
                            .relatedEntityType("DOCTOR_SCHEDULE")
                            .relatedEntityId(schedule.getId())
                            .build();

                    notificationService.sendNotification(notiRequest);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to send missing check-in notification for reception user {}: {}", 
                            receptionUserId, e.getMessage());
                }
            }

            if (successCount > 0) {
                log.info("Sent {} notifications to reception users about doctor {} missing check-in ({} minutes after shift start). Skipped {} already notified.", 
                        successCount, doctorName, minutesPassed, skippedCount);
            } else if (skippedCount > 0) {
                log.debug("All {} reception users already notified about doctor {} missing check-in for schedule {}", 
                        skippedCount, doctorName, schedule.getId());
            }
        } catch (Exception e) {
            log.error("Failed to send missing check-in notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Lấy danh sách reception user IDs của một cơ sở cụ thể
     */
    private List<Integer> getReceptionUserIdsByClinic(Integer clinicId) {
        try {
            // Lấy tất cả assignments của clinic
            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByClinicId(clinicId);
            if (assignments == null || assignments.isEmpty()) {
                return List.of();
            }

            // Lấy tất cả reception user IDs trong hệ thống
            List<Integer> allReceptionUserIds = userRoleRepo.findUserIdsByRoleName("RECEPTION");
            if (allReceptionUserIds == null || allReceptionUserIds.isEmpty()) {
                return List.of();
            }

            LocalDate today = LocalDate.now();
            
            // Lọc những reception users có assignment tại clinic này
            return assignments.stream()
                    .filter(assignment -> {
                        if (assignment == null || assignment.getUser() == null) {
                            return false;
                        }
                        
                        // Chỉ lấy user đang active
                        if (!Boolean.TRUE.equals(assignment.getUser().getIsActive())) {
                            return false;
                        }
                        
                        // Kiểm tra assignment còn hiệu lực (không có endDate hoặc endDate trong tương lai)
                        if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(today)) {
                            return false;
                        }
                        
                        // Kiểm tra user có role RECEPTION
                        return allReceptionUserIds.contains(assignment.getUser().getId());
                    })
                    .map(assignment -> assignment.getUser().getId())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get reception user IDs for clinic {}: {}", clinicId, e.getMessage(), e);
            return List.of();
        }
    }
}
