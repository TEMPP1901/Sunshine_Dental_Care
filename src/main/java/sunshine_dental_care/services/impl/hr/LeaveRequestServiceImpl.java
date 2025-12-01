package sunshine_dental_care.services.impl.hr;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.LeaveRequestRequest;
import sunshine_dental_care.dto.hrDTO.LeaveRequestResponse;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.LeaveRequest;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.hr.LeaveRequestService;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private final LeaveRequestRepo leaveRequestRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AttendanceStatusCalculator attendanceStatusCalculator;
    private final sunshine_dental_care.repositories.hr.AttendanceRepository attendanceRepository;
    private final ClinicResolutionService clinicResolutionService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public LeaveRequestResponse createLeaveRequest(Integer userId, LeaveRequestRequest request) {
        log.info("Creating leave request for user {}: {} to {}", userId, request.getStartDate(), request.getEndDate());

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot create leave request for past dates");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Tự động resolve clinicId nếu không được cung cấp
        Integer clinicId = clinicResolutionService.resolveClinicId(userId, request.getClinicId());
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        // Validate shiftType for doctors
        String shiftType = request.getShiftType();
        if (isDoctor && shiftType != null && !shiftType.trim().isEmpty()) {
            shiftType = shiftType.toUpperCase().trim();
            if (!"MORNING".equals(shiftType) && !"AFTERNOON".equals(shiftType) && !"FULL_DAY".equals(shiftType)) {
                throw new IllegalArgumentException("Invalid shiftType for doctor. Must be MORNING, AFTERNOON, or FULL_DAY");
            }
        } else if (isDoctor) {
            // Default to FULL_DAY if not specified for doctors
            shiftType = "FULL_DAY";
        } else {
            // Non-doctors always have FULL_DAY
            shiftType = "FULL_DAY";
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setUser(user);
        leaveRequest.setClinic(clinic);
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setType(request.getType());
        leaveRequest.setStatus("PENDING");
        leaveRequest.setReason(request.getReason());
        leaveRequest.setShiftType(isDoctor ? shiftType : "FULL_DAY");
        leaveRequest.setCreatedAt(Instant.now());
        leaveRequest.setUpdatedAt(Instant.now());

        leaveRequest = leaveRequestRepo.save(leaveRequest);

        // Không update doctor schedule ở bước tạo đơn (chỉ update khi duyệt)
        log.info("Leave request created for user {} (doctor: {}) from {} to {} - status: PENDING",
                userId, isDoctor, request.getStartDate(), request.getEndDate());

        try {
            List<Integer> hrUserIds = getHrUserIds();
            String dateRange = request.getStartDate().equals(request.getEndDate()) 
                ? request.getStartDate().toString()
                : request.getStartDate() + " to " + request.getEndDate();
            
            for (Integer hrUserId : hrUserIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(hrUserId)
                            .type("LEAVE_REQUEST_CREATED")
                            .priority("MEDIUM")
                            .title("New Leave Request")
                            .message(String.format("%s has submitted a leave request from %s. Type: %s", 
                                    user.getFullName(), dateRange, request.getType()))
                            .relatedEntityType("LEAVE_REQUEST")
                            .relatedEntityId(leaveRequest.getId())
                            .build();
                    notificationService.sendNotification(notiRequest);
                    log.info("Notification sent to HR user {} about new leave request {}", hrUserId, leaveRequest.getId());
                } catch (Exception e) {
                    log.error("Failed to send notification to HR user {}: {}", hrUserId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send notifications for leave request creation: {}", e.getMessage());
        }

        return mapToResponse(leaveRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getMyLeaveRequests(Integer userId) {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findByUserIdOrderByCreatedAtDesc(userId);
        return leaveRequests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyLeaveRequests(Integer userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequest> leaveRequests = leaveRequestRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        // Force load relationships để tránh lazy loading exception
        leaveRequests.getContent().forEach(lr -> {
            if (lr.getUser() != null) {
                lr.getUser().getId(); // Trigger lazy load
                lr.getUser().getUsername(); // Trigger lazy load
                lr.getUser().getFullName(); // Trigger lazy load
            }
            if (lr.getClinic() != null) {
                lr.getClinic().getId(); // Trigger lazy load
                lr.getClinic().getClinicName(); // Trigger lazy load
            }
            if (lr.getApprovedBy() != null) {
                lr.getApprovedBy().getId(); // Trigger lazy load
                lr.getApprovedBy().getFullName(); // Trigger lazy load
            }
        });
        return leaveRequests.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveRequestResponse getLeaveRequestById(Integer leaveRequestId, Integer userId) {
        LeaveRequest leaveRequest = leaveRequestRepo.findById(leaveRequestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!leaveRequest.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied: You can only view your own leave requests");
        }

        return mapToResponse(leaveRequest);
    }

    @Override
    @Transactional
    public void cancelLeaveRequest(Integer leaveRequestId, Integer userId) {
        LeaveRequest leaveRequest = leaveRequestRepo.findById(leaveRequestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!leaveRequest.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied: You can only cancel your own leave requests");
        }

        if (!"PENDING".equals(leaveRequest.getStatus())) {
            throw new RuntimeException("Cannot cancel leave request with status: " + leaveRequest.getStatus());
        }

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        // Nếu là bác sĩ và đơn đã được duyệt thì khôi phục schedule về ACTIVE
        if (isDoctor && "APPROVED".equals(leaveRequest.getStatus())) {
            updateScheduleStatusForLeave(leaveRequest, "ACTIVE");
            log.info("Restored schedules to ACTIVE for doctor {} after canceling approved leave request",
                    userId);
        }

        try {
            List<Integer> hrUserIds = getHrUserIds();
            String dateRange = leaveRequest.getStartDate().equals(leaveRequest.getEndDate()) 
                ? leaveRequest.getStartDate().toString()
                : leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate();
            
            for (Integer hrUserId : hrUserIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(hrUserId)
                            .type("LEAVE_REQUEST_CANCELLED")
                            .priority("LOW")
                            .title("Leave Request Cancelled")
                            .message(String.format("%s has cancelled their leave request from %s.", 
                                    leaveRequest.getUser().getFullName(), dateRange))
                            .relatedEntityType("LEAVE_REQUEST")
                            .relatedEntityId(leaveRequest.getId())
                            .build();
                    notificationService.sendNotification(notiRequest);
                    log.info("Notification sent to HR user {} about cancelled leave request {}", 
                            hrUserId, leaveRequestId);
                } catch (Exception e) {
                    log.error("Failed to send notification to HR user {}: {}", hrUserId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send notifications for leave request cancellation: {}", e.getMessage());
        }

        leaveRequestRepo.delete(leaveRequest);
        log.info("Leave request {} canceled by user {}", leaveRequestId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getAllPendingLeaveRequests() {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findAllPending();
        return leaveRequests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getAllLeaveRequests(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequest> leaveRequests;

        if (status != null && !status.isEmpty()) {
            leaveRequests = leaveRequestRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            leaveRequests = leaveRequestRepo.findAll(pageable);
        }

        return leaveRequests.map(this::mapToResponse);
    }

    @Override
    @Transactional
    public LeaveRequestResponse processLeaveRequest(Integer hrUserId, LeaveRequestRequest request) {
        log.info("Processing leave request {} with action {} by HR user {}",
                request.getLeaveRequestId(), request.getAction(), hrUserId);

        LeaveRequest leaveRequest = leaveRequestRepo.findById(request.getLeaveRequestId())
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        if (!"PENDING".equals(leaveRequest.getStatus())) {
            throw new RuntimeException("Cannot process leave request with status: " + leaveRequest.getStatus());
        }

        User hrUser = userRepo.findById(hrUserId)
                .orElseThrow(() -> new RuntimeException("HR user not found"));

        String action = request.getAction().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new IllegalArgumentException("Action must be APPROVE or REJECT");
        }

        leaveRequest.setStatus(action.equals("APPROVE") ? "APPROVED" : "REJECTED");
        leaveRequest.setApprovedBy(hrUser);
        leaveRequest.setUpdatedAt(Instant.now());

        if (request.getComment() != null && !request.getComment().trim().isEmpty()) {
            String originalReason = leaveRequest.getReason();
            leaveRequest.setReason(originalReason + " [HR Comment: " + request.getComment() + "]");
        }

        leaveRequest = leaveRequestRepo.save(leaveRequest);

        Integer userId = leaveRequest.getUser().getId();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        // Nếu là bác sĩ thì update schedule tương ứng với quyết định approve/reject
        if (isDoctor) {
            if ("APPROVED".equals(leaveRequest.getStatus())) {
                updateScheduleStatusForLeave(leaveRequest, "INACTIVE");
                log.info("Set schedules to INACTIVE for doctor {} after approving leave request",
                        userId);
            } else if ("REJECTED".equals(leaveRequest.getStatus())) {
                updateScheduleStatusForLeave(leaveRequest, "ACTIVE");
                log.info("Restored schedules to ACTIVE for doctor {} after rejecting leave request",
                        userId);
            }
        }

        // Tạo hoặc cập nhật attendance cho những ngày được duyệt nghỉ (APPROVED_ABSENCE), hoặc cập nhật lại khi bị reject
        if ("APPROVED".equals(leaveRequest.getStatus())) {
            createOrUpdateAttendanceForApprovedLeave(leaveRequest);
        } else if ("REJECTED".equals(leaveRequest.getStatus())) {
            updateAttendanceForRejectedLeave(leaveRequest);
        }

        try {
            User requestUser = leaveRequest.getUser();
            String dateRange = leaveRequest.getStartDate().equals(leaveRequest.getEndDate()) 
                ? leaveRequest.getStartDate().toString()
                : leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate();
            
            String notificationTitle = "APPROVED".equals(leaveRequest.getStatus()) 
                ? "Leave Request Approved" 
                : "Leave Request Rejected";
            
            String notificationMessage = "APPROVED".equals(leaveRequest.getStatus())
                ? String.format("Your leave request from %s has been approved by %s.", 
                    dateRange, hrUser.getFullName())
                : String.format("Your leave request from %s has been rejected by %s.", 
                    dateRange, hrUser.getFullName());
            
            if (request.getComment() != null && !request.getComment().trim().isEmpty()) {
                notificationMessage += " Comment: " + request.getComment();
            }
            
            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(requestUser.getId())
                    .type("APPROVED".equals(leaveRequest.getStatus()) 
                        ? "LEAVE_REQUEST_APPROVED" 
                        : "LEAVE_REQUEST_REJECTED")
                    .priority("APPROVED".equals(leaveRequest.getStatus()) ? "MEDIUM" : "HIGH")
                    .title(notificationTitle)
                    .message(notificationMessage)
                    .relatedEntityType("LEAVE_REQUEST")
                    .relatedEntityId(leaveRequest.getId())
                    .build();
            notificationService.sendNotification(notiRequest);
            log.info("Notification sent to user {} about leave request {} being {}", 
                    requestUser.getId(), leaveRequest.getId(), leaveRequest.getStatus());
        } catch (Exception e) {
            log.error("Failed to send notification to user about leave request processing: {}", e.getMessage());
        }

        return mapToResponse(leaveRequest);
    }

    // Cập nhật trạng thái lịch làm việc của bác sĩ trong thời gian xin nghỉ
    // Nếu có shiftType, chỉ update schedule của ca đó; nếu FULL_DAY hoặc null, update tất cả
    private void updateScheduleStatusForLeave(LeaveRequest leaveRequest, String status) {
        Integer userId = leaveRequest.getUser().getId();
        LocalDate startDate = leaveRequest.getStartDate();
        LocalDate endDate = leaveRequest.getEndDate();
        String shiftType = leaveRequest.getShiftType();

        LocalDate currentDate = startDate;
        int totalUpdated = 0;
        LocalTime lunchBreakStart = LocalTime.of(11, 0);

        while (!currentDate.isAfter(endDate)) {
            List<DoctorSchedule> daySchedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(
                    userId, currentDate);

            for (DoctorSchedule schedule : daySchedules) {
                if (schedule != null) {
                    // Nếu có shiftType cụ thể (MORNING/AFTERNOON), chỉ update schedule của ca đó
                    if (shiftType != null && !shiftType.isEmpty() && !"FULL_DAY".equals(shiftType)) {
                        LocalTime startTime = schedule.getStartTime();
                        if (startTime == null) {
                            continue;
                        }
                        
                        String scheduleShiftType = startTime.isBefore(lunchBreakStart) ? "MORNING" : "AFTERNOON";
                        if (!shiftType.equals(scheduleShiftType)) {
                            // Skip schedule không khớp với ca xin nghỉ
                            continue;
                        }
                    }
                    // Nếu FULL_DAY hoặc null, update tất cả schedules

                    if (!status.equals(schedule.getStatus())) {
                        schedule.setStatus(status);
                        try {
                            doctorScheduleRepo.save(schedule);
                            totalUpdated++;
                            log.debug("Updated schedule {} to {} for doctor {} at clinic {} on {} (shiftType: {})",
                                    schedule.getId(), status, userId,
                                    schedule.getClinic() != null ? schedule.getClinic().getId() : "unknown",
                                    currentDate, shiftType != null ? shiftType : "FULL_DAY");
                        } catch (Exception e) {
                            log.warn("Failed to update schedule {} status: {}", schedule.getId(), e.getMessage());
                        }
                    }
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Updated {} schedules to {} for doctor {} from {} to {} (shiftType: {})",
                totalUpdated, status, userId, startDate, endDate, shiftType != null ? shiftType : "FULL_DAY");
    }


    // Tạo hoặc cập nhật attendance cho các ngày nghỉ đã duyệt thành APPROVED_ABSENCE, bỏ qua chủ nhật và không ghi đè nếu đã check-in
    private void createOrUpdateAttendanceForApprovedLeave(LeaveRequest leaveRequest) {
        Integer userId = leaveRequest.getUser().getId();
        Integer clinicId = leaveRequest.getClinic().getId();
        LocalDate startDate = leaveRequest.getStartDate();
        LocalDate endDate = leaveRequest.getEndDate();

        LocalDate currentDate = startDate;
        int totalCreated = 0;

        while (!currentDate.isAfter(endDate)) {
            // Bỏ qua Chủ nhật
            if (currentDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            java.util.Optional<sunshine_dental_care.entities.Attendance> existingAttendance =
                    attendanceRepository.findByUserIdAndClinicIdAndWorkDate(userId, clinicId, currentDate);

            sunshine_dental_care.entities.Attendance attendance;
            if (existingAttendance.isPresent()) {
                attendance = existingAttendance.get();
                // Không cập nhật nếu đã check in
                if (attendance.getCheckInTime() == null) {
                    attendance.setAttendanceStatus("APPROVED_ABSENCE");
                    attendanceRepository.save(attendance);
                    totalCreated++;
                    log.debug("Updated attendance record to APPROVED_ABSENCE for user {} at clinic {} on {}",
                            userId, clinicId, currentDate);
                }
            } else {
                attendance = new sunshine_dental_care.entities.Attendance();
                attendance.setUserId(userId);
                attendance.setClinicId(clinicId);
                attendance.setWorkDate(currentDate);
                attendance.setAttendanceStatus("APPROVED_ABSENCE");
                attendance.setCreatedAt(Instant.now());
                attendance.setUpdatedAt(Instant.now());
                attendanceRepository.save(attendance);
                totalCreated++;
                log.debug("Created attendance record with APPROVED_ABSENCE for user {} at clinic {} on {}",
                        userId, clinicId, currentDate);
            }

            currentDate = currentDate.plusDays(1);
        }

        log.info("Created/updated {} attendance records with APPROVED_ABSENCE for user {} from {} to {}",
                totalCreated, userId, startDate, endDate);
    }

    // Nếu đơn bị từ chối và ngày đó có attendance là APPROVED_ABSENCE (nhưng chưa check-in) thì đổi lại thành ABSENT
    private void updateAttendanceForRejectedLeave(LeaveRequest leaveRequest) {
        Integer userId = leaveRequest.getUser().getId();
        Integer clinicId = leaveRequest.getClinic().getId();
        LocalDate startDate = leaveRequest.getStartDate();
        LocalDate endDate = leaveRequest.getEndDate();

        LocalDate currentDate = startDate;
        int totalUpdated = 0;

        while (!currentDate.isAfter(endDate)) {
            // Bỏ qua Chủ nhật
            if (currentDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            java.util.Optional<sunshine_dental_care.entities.Attendance> existingAttendance =
                    attendanceRepository.findByUserIdAndClinicIdAndWorkDate(userId, clinicId, currentDate);

            if (existingAttendance.isPresent()) {
                sunshine_dental_care.entities.Attendance attendance = existingAttendance.get();
                // Chỉ update nếu attendance là APPROVED_ABSENCE và chưa có check-in
                if ("APPROVED_ABSENCE".equals(attendance.getAttendanceStatus())
                        && attendance.getCheckInTime() == null) {
                    attendance.setAttendanceStatus("ABSENT");
                    attendanceRepository.save(attendance);
                    totalUpdated++;
                    log.debug("Updated attendance record from APPROVED_ABSENCE to ABSENT for user {} at clinic {} on {}",
                            userId, clinicId, currentDate);
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        if (totalUpdated > 0) {
            log.info("Updated {} attendance records from APPROVED_ABSENCE to ABSENT for user {} from {} to {}",
                    totalUpdated, userId, startDate, endDate);
        }
    }

    // Chuyển đổi entity LeaveRequest sang DTO LeaveRequestResponse
    private LeaveRequestResponse mapToResponse(LeaveRequest leaveRequest) {
        if (leaveRequest == null) {
            throw new IllegalArgumentException("LeaveRequest cannot be null");
        }
        
        LeaveRequestResponse response = new LeaveRequestResponse();
        response.setId(leaveRequest.getId());
        
        // Null check cho User
        if (leaveRequest.getUser() != null) {
            response.setUserId(leaveRequest.getUser().getId());
            response.setUserName(leaveRequest.getUser().getUsername());
            response.setUserFullName(leaveRequest.getUser().getFullName());
        }
        
        // Null check cho Clinic
        if (leaveRequest.getClinic() != null) {
            response.setClinicId(leaveRequest.getClinic().getId());
            response.setClinicName(leaveRequest.getClinic().getClinicName());
        }
        
        response.setStartDate(leaveRequest.getStartDate());
        response.setEndDate(leaveRequest.getEndDate());
        response.setType(leaveRequest.getType());
        response.setShiftType(leaveRequest.getShiftType());
        response.setStatus(leaveRequest.getStatus());
        response.setReason(leaveRequest.getReason());

        if (leaveRequest.getApprovedBy() != null) {
            response.setApprovedBy(leaveRequest.getApprovedBy().getId());
            response.setApprovedByName(leaveRequest.getApprovedBy().getFullName());
        }

        response.setCreatedAt(leaveRequest.getCreatedAt());
        response.setUpdatedAt(leaveRequest.getUpdatedAt());

        return response;
    }

    // Lấy danh sách ID của các user HR
    private List<Integer> getHrUserIds() {
        try {
            List<UserRole> allUserRoles = userRoleRepo.findAll();
            return allUserRoles.stream()
                    .filter(ur -> ur != null 
                            && Boolean.TRUE.equals(ur.getIsActive())
                            && ur.getRole() != null 
                            && "HR".equalsIgnoreCase(ur.getRole().getRoleName())
                            && ur.getUser() != null
                            && Boolean.TRUE.equals(ur.getUser().getIsActive()))
                    .map(ur -> ur.getUser().getId())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get HR user IDs: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
