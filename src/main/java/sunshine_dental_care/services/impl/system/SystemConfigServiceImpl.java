package sunshine_dental_care.services.impl.system;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.Holiday;
import sunshine_dental_care.entities.SystemConfig;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.repositories.system.HolidayRepo;
import sunshine_dental_care.repositories.system.SystemConfigRepo;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.system.SystemConfigService;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepo systemConfigRepo;
    private final HolidayRepo holidayRepo;
    private final ClinicRepo clinicRepo;
    private final NotificationService notificationService;
    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;

    @Override
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepo.findAll();
    }

    @Override
    public SystemConfig getConfig(String key) {
        return systemConfigRepo.findByConfigKey(key).orElse(null);
    }

    @Override
    @Transactional
    public SystemConfig updateConfig(String key, String value, String description) {
        Optional<SystemConfig> existing = systemConfigRepo.findByConfigKey(key);
        SystemConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setConfigValue(value);
            if (description != null) {
                config.setDescription(description);
            }
        } else {
            config = SystemConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description(description)
                    .build();
        }
        return systemConfigRepo.save(config);
    }

    @Override
    public Map<String, String> getPublicConfigs() {
        List<SystemConfig> configs = systemConfigRepo.findAll();
        Map<String, String> result = new HashMap<>();
        for (SystemConfig cfg : configs) {
            result.put(cfg.getConfigKey(), cfg.getConfigValue());
        }
        return result;
    }

    @Override
    public List<Holiday> getAllHolidays() {
        return holidayRepo.findAll();
    }

    // Thêm holiday, set isActive cho clinic khi cần thiết
    @Override
    @Transactional
    public Holiday addHoliday(LocalDate date, String name, Boolean isRecurring, Integer duration, Integer clinicId) {
        LocalDate today = LocalDate.now();
        
        // Validation: Không cho phép tạo holiday cho ngày quá khứ hoặc hiện tại
        if (date.isBefore(today) || date.isEqual(today)) {
            throw new IllegalArgumentException(
                String.format("Không thể tạo ngày nghỉ lễ cho ngày quá khứ hoặc hiện tại. Ngày bắt đầu phải là ngày tương lai (sau %s)", today)
            );
        }
        
        Holiday holiday = Holiday.builder()
                .date(date)
                .name(name)
                .isRecurring(isRecurring != null ? isRecurring : false)
                .duration(duration != null && duration > 0 ? duration : 1)
                .clinicId(clinicId)
                .build();
        Holiday savedHoliday = holidayRepo.save(holiday);
        LocalDate holidayEndDate = date.plusDays((duration != null && duration > 0 ? duration : 1) - 1);

        // Nếu holiday đang diễn ra thì set inactive cho clinic
        if (!date.isAfter(today) && !holidayEndDate.isBefore(today)) {
            if (clinicId != null) {
                Optional<Clinic> clinicOpt = clinicRepo.findById(clinicId);
                if (clinicOpt.isPresent()) {
                    Clinic clinic = clinicOpt.get();
                    if (Boolean.TRUE.equals(clinic.getIsActive())) {
                        clinic.setIsActive(false);
                        clinic.setDeactivatedByHoliday(true);
                        clinicRepo.save(clinic);
                        log.info("Set clinic {} ({}) to inactive due to holiday {} from {} to {}", 
                                clinicId, clinic.getClinicName(), name, date, holidayEndDate);
                    }
                } else {
                    log.warn("Clinic with ID {} not found when setting inactive for holiday", clinicId);
                }
            } else {
                // Nghỉ toàn bộ, set tất cả clinic về inactive
                List<Clinic> allClinics = clinicRepo.findAll();
                int inactiveCount = 0;
                for (Clinic clinic : allClinics) {
                    if (Boolean.TRUE.equals(clinic.getIsActive())) {
                        clinic.setIsActive(false);
                        clinic.setDeactivatedByHoliday(true);
                        clinicRepo.save(clinic);
                        inactiveCount++;
                        log.info("Set clinic {} ({}) to inactive due to global holiday {} from {} to {}", 
                                clinic.getId(), clinic.getClinicName(), name, date, holidayEndDate);
                    }
                }
                log.info("Set {} clinics to inactive due to global holiday {} from {} to {}", 
                        inactiveCount, name, date, holidayEndDate);
            }
        }

        // Xử lý các schedule đã tồn tại trùng với holiday: vô hiệu hóa chúng
        try {
            List<DoctorSchedule> conflictingSchedules = doctorScheduleRepo.findByDateRangeAndClinic(
                    date, holidayEndDate, clinicId);
            
            if (!conflictingSchedules.isEmpty()) {
                log.info("Found {} existing schedules conflicting with holiday {} from {} to {} at clinic {}", 
                        conflictingSchedules.size(), name, date, holidayEndDate, 
                        clinicId != null ? clinicId : "ALL");
                
                Set<Integer> affectedDoctorIds = new HashSet<>();
                int cancelledCount = 0;
                
                for (DoctorSchedule schedule : conflictingSchedules) {
                    // Chỉ vô hiệu hóa các schedule đang ACTIVE hoặc null (mặc định là ACTIVE)
                    if (schedule.getStatus() == null || "ACTIVE".equals(schedule.getStatus())) {
                        schedule.setStatus("CANCELLED");
                        schedule.setUpdatedAt(java.time.Instant.now());
                        doctorScheduleRepo.save(schedule);
                        cancelledCount++;
                        
                        if (schedule.getDoctor() != null && schedule.getDoctor().getId() != null) {
                            affectedDoctorIds.add(schedule.getDoctor().getId());
                        }
                        
                        log.debug("Cancelled schedule {} for doctor {} at clinic {} on {} due to holiday", 
                                schedule.getId(), 
                                schedule.getDoctor() != null ? schedule.getDoctor().getId() : "unknown",
                                schedule.getClinic() != null ? schedule.getClinic().getId() : "unknown",
                                schedule.getWorkDate());
                    }
                }
                
                log.info("Cancelled {} schedules due to holiday {} from {} to {}", 
                        cancelledCount, name, date, holidayEndDate);
                
                // Gửi thông báo cho các bác sĩ bị ảnh hưởng
                if (!affectedDoctorIds.isEmpty()) {
                    String dateRange = duration != null && duration > 1
                            ? String.format("%s đến %s", date, holidayEndDate)
                            : date.toString();
                    
                    String clinicInfo = "";
                    if (clinicId != null) {
                        Optional<Clinic> clinicOpt = clinicRepo.findById(clinicId);
                        if (clinicOpt.isPresent()) {
                            clinicInfo = String.format(" tại %s", clinicOpt.get().getClinicName());
                        }
                    } else {
                        clinicInfo = " (Toàn hệ thống)";
                    }
                    
                    String message = String.format(
                            "Lịch làm việc của bạn đã bị hủy do ngày nghỉ lễ: %s từ %s%s. Vui lòng kiểm tra lại lịch của bạn.", 
                            name, dateRange, clinicInfo);
                    
                    for (Integer doctorId : affectedDoctorIds) {
                        try {
                            NotificationRequest notiRequest = NotificationRequest.builder()
                                    .userId(doctorId)
                                    .type("SCHEDULE_CANCELLED")
                                    .priority("HIGH")
                                    .title("Lịch làm việc bị hủy do ngày nghỉ lễ")
                                    .message(message)
                                    .relatedEntityType("SCHEDULE")
                                    .relatedEntityId(null)
                                    .build();
                            
                            notificationService.sendNotification(notiRequest);
                            log.debug("Schedule cancellation notification sent to doctor {}", doctorId);
                        } catch (Exception e) {
                            log.error("Failed to send schedule cancellation notification to doctor {}: {}", 
                                    doctorId, e.getMessage());
                        }
                    }
                    log.info("Sent schedule cancellation notifications to {} affected doctors", affectedDoctorIds.size());
                }
            } else {
                log.debug("No existing schedules found conflicting with holiday {} from {} to {}", 
                        name, date, holidayEndDate);
            }
        } catch (Exception e) {
            log.error("Failed to handle conflicting schedules for holiday {}: {}", name, e.getMessage(), e);
            // Không throw exception để không làm rollback việc tạo holiday
        }

        // Gửi thông báo realtime cho nhân viên
        // Nếu holiday chỉ cho một clinic, chỉ gửi cho nhân viên của clinic đó
        // Nếu holiday cho toàn hệ thống, gửi cho tất cả nhân viên
        try {
            List<Integer> employeeUserIds;
            if (clinicId != null) {
                // Holiday chỉ cho một clinic cụ thể - chỉ gửi cho nhân viên của clinic đó
                // Truyền date range để lấy chính xác bác sĩ có schedule trong khoảng thời gian holiday
                employeeUserIds = getEmployeeUserIdsByClinic(clinicId, date, holidayEndDate);
                log.info("Sending holiday notification to {} employees at clinic {}", employeeUserIds.size(), clinicId);
            } else {
                // Holiday cho toàn hệ thống - gửi cho tất cả nhân viên
                employeeUserIds = getAllEmployeeUserIds();
                log.info("Sending holiday notification to {} employees (all clinics)", employeeUserIds.size());
            }

            String dateRange = duration != null && duration > 1
                    ? String.format("%s đến %s", date, holidayEndDate)
                    : date.toString();

            String clinicInfo = "";
            if (clinicId != null) {
                Optional<Clinic> clinicOpt = clinicRepo.findById(clinicId);
                if (clinicOpt.isPresent()) {
                    clinicInfo = String.format(" tại %s", clinicOpt.get().getClinicName());
                }
            } else {
                clinicInfo = " (Toàn hệ thống)";
            }

            String message = String.format("Ngày nghỉ lễ: %s từ %s%s", name, dateRange, clinicInfo);

            for (Integer userId : employeeUserIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(userId)
                            .type("HOLIDAY_CREATED")
                            .priority("MEDIUM")
                            .title("Thông báo ngày nghỉ lễ")
                            .message(message)
                            .relatedEntityType("HOLIDAY")
                            .relatedEntityId(savedHoliday.getId())
                            .build();

                    notificationService.sendNotification(notiRequest);
                    log.debug("Holiday notification sent to employee user {}", userId);
                } catch (Exception e) {
                    log.error("Failed to send holiday notification to employee user {}: {}", userId, e.getMessage());
                }
            }
            log.info("Successfully sent holiday notifications to {} employees", employeeUserIds.size());
        } catch (Exception e) {
            log.error("Failed to send holiday notifications to employees: {}", e.getMessage(), e);
            // Không throw exception để không làm rollback việc tạo holiday
        }

        return savedHoliday;
    }

    // Xóa holiday và xử lý khôi phục trạng thái clinic và schedule nếu không còn ngày nghỉ phù hợp
    @Override
    @Transactional
    public void deleteHoliday(Integer id) {
        Optional<Holiday> holidayOpt = holidayRepo.findById(id);
        if (!holidayOpt.isPresent()) {
            holidayRepo.deleteById(id);
            // Vẫn gọi restore để đảm bảo clinic được restore nếu cần
            restoreClinicsAfterHolidayEnded();
            return;
        }
        
        Holiday holiday = holidayOpt.get();
        String holidayName = holiday.getName();
        LocalDate holidayDate = holiday.getDate();
        Integer holidayDuration = holiday.getDuration();
        Integer holidayClinicId = holiday.getClinicId();
        
        // Tính toán ngày kết thúc holiday
        int duration = holidayDuration != null && holidayDuration > 0 ? holidayDuration : 1;
        LocalDate holidayEndDate = holidayDate.plusDays(duration - 1);
        
        // Xóa holiday
        holidayRepo.deleteById(id);
        log.info("Deleted holiday {} (ID: {}) from {} to {}", holidayName, id, holidayDate, holidayEndDate);

        // Restore các schedule bị CANCELLED do holiday này
        // Lấy danh sách holiday còn lại để kiểm tra xem schedule có bị ảnh hưởng bởi holiday khác không
        List<Holiday> remainingHolidays = holidayRepo.findAll();
        
        try {
            // Sử dụng query bao gồm cả CANCELLED để tìm các schedule cần restore
            List<DoctorSchedule> cancelledSchedules = doctorScheduleRepo.findByDateRangeAndClinicAllStatus(
                    holidayDate, holidayEndDate, holidayClinicId);
            
            if (!cancelledSchedules.isEmpty()) {
                int restoredCount = 0;
                Set<Integer> affectedDoctorIds = new HashSet<>();
                
                for (DoctorSchedule schedule : cancelledSchedules) {
                    // Chỉ restore các schedule bị CANCELLED (có thể do holiday này)
                    if ("CANCELLED".equals(schedule.getStatus())) {
                        // Kiểm tra xem schedule có nằm trong khoảng thời gian holiday không
                        LocalDate scheduleDate = schedule.getWorkDate();
                        if (scheduleDate != null && 
                            !scheduleDate.isBefore(holidayDate) && 
                            !scheduleDate.isAfter(holidayEndDate)) {
                            
                            // Kiểm tra clinic có match không (nếu holiday cho clinic cụ thể)
                            if (holidayClinicId == null || 
                                (schedule.getClinic() != null && schedule.getClinic().getId().equals(holidayClinicId))) {
                                
                                // Kiểm tra xem còn holiday nào khác ảnh hưởng đến schedule này không
                                boolean hasOtherHoliday = false;
                                Integer scheduleClinicId = schedule.getClinic() != null ? schedule.getClinic().getId() : null;
                                
                                for (Holiday h : remainingHolidays) {
                                    LocalDate hStart = h.getDate();
                                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                                        try {
                                            hStart = hStart.withYear(scheduleDate.getYear());
                                        } catch (Exception e) {
                                            continue;
                                        }
                                    }
                                    Integer hDurationValue = h.getDuration();
                                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                                    LocalDate hEnd = hStart.plusDays(hDuration - 1);
                                    
                                    // Kiểm tra schedule có nằm trong khoảng holiday này không
                                    if (!scheduleDate.isBefore(hStart) && !scheduleDate.isAfter(hEnd)) {
                                        // Kiểm tra clinic có match không
                                        Integer hClinicId = h.getClinicId();
                                        if (hClinicId == null || (scheduleClinicId != null && hClinicId.equals(scheduleClinicId))) {
                                            hasOtherHoliday = true;
                                            break;
                                        }
                                    }
                                }
                                
                                // Chỉ restore nếu không còn holiday nào khác ảnh hưởng
                                if (!hasOtherHoliday) {
                                    // Restore về ACTIVE
                                    schedule.setStatus("ACTIVE");
                                    schedule.setUpdatedAt(java.time.Instant.now());
                                    doctorScheduleRepo.save(schedule);
                                    restoredCount++;
                                    
                                    if (schedule.getDoctor() != null && schedule.getDoctor().getId() != null) {
                                        affectedDoctorIds.add(schedule.getDoctor().getId());
                                    }
                                    
                                    log.debug("Restored schedule {} for doctor {} at clinic {} on {} after deleting holiday", 
                                            schedule.getId(), 
                                            schedule.getDoctor() != null ? schedule.getDoctor().getId() : "unknown",
                                            schedule.getClinic() != null ? schedule.getClinic().getId() : "unknown",
                                            schedule.getWorkDate());
                                } else {
                                    log.debug("Schedule {} not restored - still affected by another holiday", schedule.getId());
                                }
                            }
                        }
                    }
                }
                
                log.info("Restored {} schedules to ACTIVE after deleting holiday {} from {} to {}", 
                        restoredCount, holidayName, holidayDate, holidayEndDate);
                
                // Gửi thông báo cho các bác sĩ được restore schedule
                if (!affectedDoctorIds.isEmpty()) {
                    String dateRange = duration > 1
                            ? String.format("%s đến %s", holidayDate, holidayEndDate)
                            : holidayDate.toString();
                    
                    String clinicInfo = "";
                    if (holidayClinicId != null) {
                        Optional<Clinic> clinicOpt = clinicRepo.findById(holidayClinicId);
                        if (clinicOpt.isPresent()) {
                            clinicInfo = String.format(" tại %s", clinicOpt.get().getClinicName());
                        }
                    } else {
                        clinicInfo = " (Toàn hệ thống)";
                    }
                    
                    String message = String.format(
                            "Lịch làm việc của bạn đã được khôi phục sau khi hủy ngày nghỉ lễ: %s từ %s%s. Vui lòng kiểm tra lại lịch của bạn.", 
                            holidayName, dateRange, clinicInfo);
                    
                    for (Integer doctorId : affectedDoctorIds) {
                        try {
                            NotificationRequest notiRequest = NotificationRequest.builder()
                                    .userId(doctorId)
                                    .type("SCHEDULE_RESTORED")
                                    .priority("MEDIUM")
                                    .title("Lịch làm việc đã được khôi phục")
                                    .message(message)
                                    .relatedEntityType("SCHEDULE")
                                    .relatedEntityId(null)
                                    .build();
                            
                            notificationService.sendNotification(notiRequest);
                            log.debug("Schedule restoration notification sent to doctor {}", doctorId);
                        } catch (Exception e) {
                            log.error("Failed to send schedule restoration notification to doctor {}: {}", 
                                    doctorId, e.getMessage());
                        }
                    }
                    log.info("Sent schedule restoration notifications to {} affected doctors", affectedDoctorIds.size());
                }
            } else {
                log.debug("No cancelled schedules found to restore for holiday {} from {} to {}", 
                        holidayName, holidayDate, holidayEndDate);
            }
        } catch (Exception e) {
            log.error("Failed to restore schedules after deleting holiday {}: {}", holidayName, e.getMessage(), e);
            // Không throw exception để không làm rollback việc xóa holiday
        }

        // Sau khi xóa, gọi restore để đảm bảo tất cả clinic được kiểm tra và restore nếu cần
        // Điều này đảm bảo clinic được restore ngay cả khi holiday đã kết thúc nhưng clinic vẫn inactive
        restoreClinicsAfterHolidayEnded();
    }

    // Kiểm tra ngày có thuộc holiday không
    @Override
    public boolean isHoliday(LocalDate date) {
        List<Holiday> holidays = holidayRepo.findHolidaysForDate(date);
        return !holidays.isEmpty();
    }

    // Quét và tự động trả lại trạng thái active cho clinic nếu không còn holiday diễn ra
    @Override
    @Transactional
    public void restoreClinicsAfterHolidayEnded() {
        LocalDate today = LocalDate.now();
        List<Holiday> allHolidays = holidayRepo.findAll();
        List<Clinic> allClinics = clinicRepo.findAll();

        for (Clinic clinic : allClinics) {
            if (Boolean.TRUE.equals(clinic.getIsActive())) {
                continue;
            }
            boolean hasActiveHoliday = false;
            for (Holiday h : allHolidays) {
                Integer hClinicId = h.getClinicId();
                if (hClinicId == null) {
                    // Global holiday
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveHoliday = true;
                        break;
                    }
                } else if (hClinicId.equals(clinic.getId())) {
                    // Holiday cho từng clinic
                    LocalDate hStart = h.getDate();
                    if (Boolean.TRUE.equals(h.getIsRecurring())) {
                        try {
                            hStart = hStart.withYear(today.getYear());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    Integer hDurationValue = h.getDuration();
                    int hDuration = hDurationValue != null && hDurationValue > 0 ? hDurationValue : 1;
                    LocalDate hEnd = hStart.plusDays(hDuration - 1);                  
                    if (!hStart.isAfter(today) && !hEnd.isBefore(today)) {
                        hasActiveHoliday = true;
                        break;
                    }
                }
            }
            // Nếu không có holiday active cho clinic thì set lại active
            if (!hasActiveHoliday && Boolean.TRUE.equals(clinic.getDeactivatedByHoliday())) {
                clinic.setIsActive(true);
                clinic.setDeactivatedByHoliday(false);
                clinicRepo.save(clinic);
                log.info("Auto-restored clinic {} ({}) to active - no active holidays found", 
                        clinic.getId(), clinic.getClinicName());
            }
        }
    }

    // Lấy danh sách tất cả user IDs của nhân viên (loại trừ role USER - khách hàng và ADMIN)
    private List<Integer> getAllEmployeeUserIds() {
        try {
            // Danh sách các role nhân viên (không bao gồm USER và ADMIN)
            List<String> employeeRoles = List.of("HR", "DOCTOR", "RECEPTIONIST", "NURSE", "ACCOUNTANT", "MANAGER");
            
            Set<Integer> employeeUserIds = new HashSet<>();
            
            // Lấy user IDs từ từng role nhân viên
            for (String roleName : employeeRoles) {
                try {
                    List<Integer> userIds = userRoleRepo.findUserIdsByRoleName(roleName);
                    if (userIds != null) {
                        employeeUserIds.addAll(userIds);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get user IDs for role {}: {}", roleName, e.getMessage());
                }
            }
            
            List<Integer> result = employeeUserIds.stream()
                    .sorted()
                    .collect(Collectors.toList());
            
            log.info("Found {} unique employee user IDs (excluding USER and ADMIN roles)", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to get all employee user IDs: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // Lấy danh sách user IDs của nhân viên tại một clinic cụ thể
    // Bao gồm: nhân viên có assignment + bác sĩ có schedule tại clinic đó trong khoảng thời gian
    private List<Integer> getEmployeeUserIdsByClinic(Integer clinicId, LocalDate holidayStart, LocalDate holidayEnd) {
        try {
            LocalDate today = LocalDate.now();
            Set<Integer> employeeUserIds = new HashSet<>();
            
            // 1. Lấy từ UserClinicAssignment (nhân viên đã được assign vào clinic)
            try {
                List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByClinicId(clinicId);
                if (assignments != null && !assignments.isEmpty()) {
                    for (UserClinicAssignment assignment : assignments) {
                        if (assignment == null || assignment.getUser() == null) {
                            continue;
                        }
                        
                        User user = assignment.getUser();
                        // Chỉ lấy user đang active
                        if (!Boolean.TRUE.equals(user.getIsActive())) {
                            continue;
                        }
                        
                        // Kiểm tra assignment còn hiệu lực (không có endDate hoặc endDate trong tương lai)
                        if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(today)) {
                            continue;
                        }
                        
                        // Kiểm tra user có role nhân viên (không phải USER hoặc ADMIN)
                        try {
                            List<String> employeeRoles = List.of("HR", "DOCTOR", "RECEPTIONIST", "NURSE", "ACCOUNTANT", "MANAGER");
                            boolean hasEmployeeRole = false;
                            
                            for (String roleName : employeeRoles) {
                                List<Integer> userIds = userRoleRepo.findUserIdsByRoleName(roleName);
                                if (userIds != null && userIds.contains(user.getId())) {
                                    hasEmployeeRole = true;
                                    break;
                                }
                            }
                            
                            if (hasEmployeeRole) {
                                employeeUserIds.add(user.getId());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check role for user {}: {}", user.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get employees from UserClinicAssignment for clinic {}: {}", clinicId, e.getMessage());
            }
            
            // 2. Lấy từ DoctorSchedule (bác sĩ có schedule tại clinic này trong khoảng thời gian holiday, kể cả chưa có assignment)
            // Mở rộng thêm 1 tháng trước và sau để đảm bảo không bỏ sót bác sĩ có schedule gần đó
            try {
                LocalDate searchStart = holidayStart.minusMonths(1);
                LocalDate searchEnd = holidayEnd.plusMonths(1);
                
                List<DoctorSchedule> schedules = doctorScheduleRepo.findByDateRangeAndClinic(
                        searchStart, searchEnd, clinicId);
                
                if (schedules != null && !schedules.isEmpty()) {
                    for (DoctorSchedule schedule : schedules) {
                        if (schedule == null || schedule.getDoctor() == null) {
                            continue;
                        }
                        
                        User doctor = schedule.getDoctor();
                        // Chỉ lấy doctor đang active
                        if (!Boolean.TRUE.equals(doctor.getIsActive())) {
                            continue;
                        }
                        
                        // Kiểm tra doctor có role DOCTOR
                        try {
                            List<Integer> doctorUserIds = userRoleRepo.findUserIdsByRoleName("DOCTOR");
                            if (doctorUserIds != null && doctorUserIds.contains(doctor.getId())) {
                                employeeUserIds.add(doctor.getId());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to check DOCTOR role for user {}: {}", doctor.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get doctors from DoctorSchedule for clinic {}: {}", clinicId, e.getMessage());
            }
            
            List<Integer> result = employeeUserIds.stream()
                    .sorted()
                    .collect(Collectors.toList());
            
            log.info("Found {} employee user IDs for clinic {} (from assignments and schedules in range {} to {})", 
                    result.size(), clinicId, holidayStart, holidayEnd);
            return result;
        } catch (Exception e) {
            log.error("Failed to get employee user IDs for clinic {}: {}", clinicId, e.getMessage(), e);
            return List.of();
        }
    }
}
