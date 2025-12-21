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
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.impl.hr.attend.AttendanceStatusCalculator;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.hr.LeaveRequestService;
import sunshine_dental_care.utils.WorkHoursConstants;

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
    private final MailService mailService;

    @Override
    @Transactional
    public LeaveRequestResponse createLeaveRequest(Integer userId, LeaveRequestRequest request) {
        // Kiểm tra ngày bắt đầu và kết thúc hợp lệ
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot create leave request for past dates");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Integer clinicId = clinicResolutionService.resolveClinicId(userId, request.getClinicId());
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        String shiftType = request.getShiftType();
        if (isDoctor && shiftType != null && !shiftType.trim().isEmpty()) {
            shiftType = shiftType.toUpperCase().trim();
            if (!"MORNING".equals(shiftType) && !"AFTERNOON".equals(shiftType) && !"FULL_DAY".equals(shiftType)) {
                throw new IllegalArgumentException(
                        "Invalid shiftType for doctor. Must be MORNING, AFTERNOON, or FULL_DAY");
            }
        } else {
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

        // Gửi thông báo cho HR khi có đơn nghỉ mới
        try {
            long startHr = System.currentTimeMillis();
            List<Integer> hrUserIds = getHrUserIds();
            long hrDuration = System.currentTimeMillis() - startHr;
            log.info("Performance: getHrUserIds took {} ms", hrDuration);

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

                    long startSend = System.currentTimeMillis();
                    notificationService.sendNotification(notiRequest);
                    long sendDuration = System.currentTimeMillis() - startSend;

                    log.info("Notification sent to HR user {} about new leave request {}", hrUserId,
                            leaveRequest.getId());
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

        // Ép load entity liên quan để tránh lazy
        leaveRequests.getContent().forEach(lr -> {
            if (lr.getUser() != null) {
                lr.getUser().getId();
                lr.getUser().getUsername();
                lr.getUser().getFullName();
            }
            if (lr.getClinic() != null) {
                lr.getClinic().getId();
                lr.getClinic().getClinicName();
            }
            if (lr.getApprovedBy() != null) {
                lr.getApprovedBy().getId();
                lr.getApprovedBy().getFullName();
            }
        });
        return leaveRequests.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveRequestResponse getLeaveRequestById(Integer leaveRequestId, Integer userId) {
        LeaveRequest leaveRequest = leaveRequestRepo.findById(leaveRequestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        // Chỉ lấy đơn của chính mình
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

        // Chỉ hủy đơn của chính mình
        if (!leaveRequest.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied: You can only cancel your own leave requests");
        }

        // Đơn chưa duyệt mới được hủy
        if (!"PENDING".equals(leaveRequest.getStatus())) {
            throw new RuntimeException("Cannot cancel leave request with status: " + leaveRequest.getStatus());
        }

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        // Nếu là bác sĩ và đơn đã được duyệt thì set lại trạng thái lịch làm việc sang ACTIVE (dự phòng)
        if (isDoctor && "APPROVED".equals(leaveRequest.getStatus())) {
            updateScheduleStatusForLeave(leaveRequest, "ACTIVE");
            log.info("Restored schedules to ACTIVE for doctor {} after canceling approved leave request",
                    userId);
        }

        // Thông báo HR về việc hủy đơn
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
    public List<LeaveRequestResponse> getAllPendingAdminLeaveRequests() {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findAllPendingAdmin();
        return leaveRequests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getLeaveRequestCounts() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        counts.put("PENDING", leaveRequestRepo.countByStatus("PENDING"));
        counts.put("PENDING_ADMIN", leaveRequestRepo.countByStatus("PENDING_ADMIN"));
        return counts;
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
    public LeaveRequestResponse processLeaveRequest(Integer approverId, LeaveRequestRequest request) {
        // Xử lý phê duyệt hoặc từ chối đơn nghỉ
        LeaveRequest leaveRequest = leaveRequestRepo.findById(request.getLeaveRequestId())
                .orElseThrow(() -> new RuntimeException("Leave request not found"));

        User approver = userRepo.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver user not found"));

        List<UserRole> approverRoles = userRoleRepo.findActiveByUserId(approverId);
        boolean isHr = approverRoles.stream().anyMatch(r -> r.getRole().getRoleName().equals("HR"));
        boolean isAdmin = approverRoles.stream().anyMatch(r -> r.getRole().getRoleName().equals("ADMIN"));

        String action = request.getAction().toUpperCase();
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new IllegalArgumentException("Action must be APPROVE or REJECT");
        }

        String currentStatus = leaveRequest.getStatus();

        if ("PENDING".equals(currentStatus)) {
            // HR hoặc Admin xác nhận bước đầu
            if (!isHr && !isAdmin) {
                throw new RuntimeException("Only HR or Admin can process PENDING requests");
            }

            if ("APPROVE".equals(action)) {
                leaveRequest.setStatus("PENDING_ADMIN");
                notifyAdminsOfPendingRequest(leaveRequest);
            } else {
                leaveRequest.setStatus("REJECTED");
                leaveRequest.setApprovedBy(approver);
                notifyUserOfResult(leaveRequest, approver, request.getComment());
            }

        } else if ("PENDING_ADMIN".equals(currentStatus)) {
            // Admin duyệt cuối
            if (!isAdmin) {
                throw new RuntimeException("Only Admin can process PENDING_ADMIN requests");
            }

            if ("APPROVE".equals(action)) {
                leaveRequest.setStatus("APPROVED");
                leaveRequest.setApprovedBy(approver);

                // Khi duyệt, cập nhật lịch và chấm công...
                finalizeApprovedLeave(leaveRequest);
                notifyUserOfResult(leaveRequest, approver, request.getComment());
                notifyHrsOfAdminResult(leaveRequest, approver, request.getComment());
            } else {
                leaveRequest.setStatus("REJECTED");
                leaveRequest.setApprovedBy(approver);
                notifyUserOfResult(leaveRequest, approver, request.getComment());
                notifyHrsOfAdminResult(leaveRequest, approver, request.getComment());
            }
        } else {
            throw new RuntimeException("Cannot process leave request with status: " + currentStatus);
        }

        leaveRequest.setUpdatedAt(Instant.now());

        // Nếu có comment của người duyệt, lưu lại kèm lý do đơn
        if (request.getComment() != null && !request.getComment().trim().isEmpty()) {
            String originalReason = leaveRequest.getReason();
            leaveRequest.setReason(
                    originalReason + " [" + approver.getFullName() + " Comment: " + request.getComment() + "]");
        }

        leaveRequest = leaveRequestRepo.save(leaveRequest);
        return mapToResponse(leaveRequest);
    }

    // Duyệt đơn nghỉ: cập nhật lịch làm việc, chấm công nếu là bác sĩ, đồng thời nếu là đơn nghỉ việc thì khóa tài khoản
    private void finalizeApprovedLeave(LeaveRequest leaveRequest) {
        Integer userId = leaveRequest.getUser().getId();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        if (isDoctor) {
            updateScheduleStatusForLeave(leaveRequest, "INACTIVE");
        }
        createOrUpdateAttendanceForApprovedLeave(leaveRequest);

        // Nếu là đơn nghỉ việc (RESIGNATION) đã được duyệt thì tự động khóa tài khoản + role
        if ("RESIGNATION".equalsIgnoreCase(leaveRequest.getType()) 
                && "APPROVED".equals(leaveRequest.getStatus())) {
            User user = leaveRequest.getUser();
            if (Boolean.TRUE.equals(user.getIsActive())) {
                user.setIsActive(false);
                user.setUpdatedAt(Instant.now());
                userRepo.save(user);

                // Khóa tham chiếu các UserRole liên quan
                List<UserRole> allUserRoles = userRoleRepo.findByUserId(userId);
                if (!allUserRoles.isEmpty()) {
                    for (UserRole userRole : allUserRoles) {
                        if (Boolean.TRUE.equals(userRole.getIsActive())) {
                            userRole.setIsActive(false);
                        }
                    }
                    userRoleRepo.saveAll(allUserRoles);
                }
                
                // Gửi email thông báo nghỉ việc đã được duyệt
                try {
                    LocalDate today = LocalDate.now();
                    LocalDate resignationDate = leaveRequest.getEndDate(); // Ngày nghỉ việc chính thức
                    String formattedResignationDate = resignationDate.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    
                    // Nếu là bác sĩ, gửi email đặc biệt với thông tin về lịch phân công
                    if (isDoctor) {
                        // Đếm số lịch phân công ACTIVE còn lại từ hôm nay đến ngày nghỉ việc
                        List<DoctorSchedule> remainingSchedules = doctorScheduleRepo.findByDoctorIdAndDateRange(
                                userId, today, resignationDate);
                        int activeSchedulesCount = (int) remainingSchedules.stream()
                                .filter(s -> s.getStatus() != null && "ACTIVE".equals(s.getStatus()))
                                .count();
                        
                        mailService.sendDoctorResignationApprovalEmail(
                                user, formattedResignationDate, activeSchedulesCount, "vi");
                        log.info("Doctor resignation approval email sent to {} with {} remaining active schedules", 
                                user.getEmail(), activeSchedulesCount);
                    } else {
                        // Nhân viên thường: gửi email thông thường
                        String reason = leaveRequest.getReason() != null ? leaveRequest.getReason() : "Đơn nghỉ việc đã được duyệt";
                        mailService.sendEmployeeDeletionEmail(user, reason, "vi");
                        log.info("Resignation approval email sent to employee: {}", user.getEmail());
                    }
                } catch (Exception e) {
                    log.error("Failed to send resignation approval email to employee {}: {}", 
                        user.getEmail(), e.getMessage(), e);
                    // Không fail transaction nếu email lỗi
                }
            }
        }
    }

    // Thông báo cho admin khi HR đã xác nhận đơn nghỉ
    private void notifyAdminsOfPendingRequest(LeaveRequest leaveRequest) {
        try {
            List<Integer> adminIds = userRoleRepo.findUserIdsByRoleName("ADMIN");
            String dateRange = leaveRequest.getStartDate().equals(leaveRequest.getEndDate())
                    ? leaveRequest.getStartDate().toString()
                    : leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate();

            for (Integer adminId : adminIds) {
                NotificationRequest notiRequest = NotificationRequest.builder()
                        .userId(adminId)
                        .type("LEAVE_REQUEST_PENDING_ADMIN")
                        .priority("HIGH")
                        .title("Leave Request Pending Admin Approval")
                        .message(String.format(
                                "HR has verified leave request for %s (%s). Waiting for your final approval.",
                                leaveRequest.getUser().getFullName(), dateRange))
                        .relatedEntityType("LEAVE_REQUEST")
                        .relatedEntityId(leaveRequest.getId())
                        .build();
                notificationService.sendNotification(notiRequest);
            }
        } catch (Exception e) {
            log.error("Failed to notify admins: {}", e.getMessage());
        }
    }

    // Thông báo cho người gửi đơn về kết quả duyệt hoặc từ chối
    private void notifyUserOfResult(LeaveRequest leaveRequest, User approver, String comment) {
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
                            dateRange, approver.getFullName())
                    : String.format("Your leave request from %s has been rejected by %s.",
                            dateRange, approver.getFullName());

            if (comment != null && !comment.trim().isEmpty()) {
                notificationMessage += " Comment: " + comment;
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
    }

    // Thông báo cho HR kết quả phê duyệt cuối từ Admin
    private void notifyHrsOfAdminResult(LeaveRequest leaveRequest, User admin, String comment) {
        try {
            List<Integer> hrIds = getHrUserIds();
            String dateRange = leaveRequest.getStartDate().equals(leaveRequest.getEndDate())
                    ? leaveRequest.getStartDate().toString()
                    : leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate();

            String action = "APPROVED".equals(leaveRequest.getStatus()) ? "Approved" : "Rejected";
            String title = "Leave Request " + action + " by Admin";
            String message = String.format("Admin %s has %s the leave request for %s (%s).",
                    admin.getFullName(), action.toLowerCase(), leaveRequest.getUser().getFullName(), dateRange);

            if (comment != null && !comment.trim().isEmpty()) {
                message += " Comment: " + comment;
            }

            for (Integer hrId : hrIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(hrId)
                            .type("LEAVE_REQUEST_PROCESSED_BY_ADMIN")
                            .priority("MEDIUM")
                            .title(title)
                            .message(message)
                            .relatedEntityType("LEAVE_REQUEST")
                            .relatedEntityId(leaveRequest.getId())
                            .build();
                    notificationService.sendNotification(notiRequest);
                } catch (Exception e) {
                    log.error("Failed to notify HR {}: {}", hrId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify HRs of Admin result: {}", e.getMessage());
        }
    }

    // Cập nhật trạng thái lịch làm việc của bác sĩ khi nghỉ (chỉ cập nhật các ca phù hợp loại ca nghỉ)
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
                    // Nếu nghỉ ca sáng/chiều kiểm tra phù hợp
                    if (shiftType != null && !shiftType.isEmpty() && !"FULL_DAY".equals(shiftType)) {
                        LocalTime startTime = schedule.getStartTime();
                        if (startTime == null) continue;
                        String scheduleShiftType = startTime.isBefore(lunchBreakStart) ? "MORNING" : "AFTERNOON";
                        if (!shiftType.equals(scheduleShiftType)) continue;
                    }
                    if (!status.equals(schedule.getStatus())) {
                        schedule.setStatus(status);
                        try {
                            doctorScheduleRepo.save(schedule);
                            totalUpdated++;
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

    // Tạo hoặc cập nhật các bản ghi chấm công sang APPROVED_ABSENCE khi đơn được duyệt. Không ghi đè nếu đã check-in
    private void createOrUpdateAttendanceForApprovedLeave(LeaveRequest leaveRequest) {
        Integer userId = leaveRequest.getUser().getId();
        Integer clinicId = leaveRequest.getClinic().getId();
        LocalDate startDate = leaveRequest.getStartDate();
        LocalDate endDate = leaveRequest.getEndDate();
        String leaveShiftType = leaveRequest.getShiftType(); // MORNING, AFTERNOON, FULL_DAY, hoặc null

        // Kiểm tra xem user có phải là bác sĩ không
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalDate currentDate = startDate;
        int totalCreated = 0;

        while (!currentDate.isAfter(endDate)) {
            // Bỏ qua Chủ nhật
            if (currentDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            if (isDoctor) {
                // BÁC SĨ: một ngày có thể có 2 bản ghi (MORNING và AFTERNOON)
                if (leaveShiftType != null && !leaveShiftType.isEmpty() && !"FULL_DAY".equals(leaveShiftType)) {
                    // Nghỉ một ca cụ thể (MORNING hoặc AFTERNOON) - tạo 1 bản ghi cho ca đó
                    java.util.Optional<sunshine_dental_care.entities.Attendance> existingAttendance = 
                            attendanceRepository.findByUserIdAndClinicIdAndWorkDateAndShiftType(
                                    userId, clinicId, currentDate, leaveShiftType);
                    
                    sunshine_dental_care.entities.Attendance attendance;
                    if (existingAttendance.isPresent()) {
                        attendance = existingAttendance.get();
                        if (attendance.getCheckInTime() == null) {
                            attendance.setAttendanceStatus("APPROVED_ABSENCE");
                            attendance.setShiftType(leaveShiftType); // Đảm bảo shiftType đúng
                            attendanceRepository.save(attendance);
                            totalCreated++;
                        }
                    } else {
                        attendance = new sunshine_dental_care.entities.Attendance();
                        attendance.setUserId(userId);
                        attendance.setClinicId(clinicId);
                        attendance.setWorkDate(currentDate);
                        attendance.setShiftType(leaveShiftType);
                        attendance.setAttendanceStatus("APPROVED_ABSENCE");
                        attendance.setCreatedAt(Instant.now());
                        attendance.setUpdatedAt(Instant.now());
                        attendanceRepository.save(attendance);
                        totalCreated++;
                    }
                } else {
                    // Nghỉ cả ngày (FULL_DAY hoặc null): tạo attendance cho mỗi ca có schedule
                    // Mỗi ca = 1 bản ghi riêng (có thể có 2 bản ghi: MORNING và AFTERNOON)
                    List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(userId, currentDate);
                    if (schedules != null && !schedules.isEmpty()) {
                        // Tạo attendance cho mỗi ca có schedule tại clinic này
                        for (DoctorSchedule schedule : schedules) {
                            if (schedule == null || schedule.getClinic() == null) continue;
                            Integer scheduleClinicId = schedule.getClinic().getId();
                            if (scheduleClinicId == null) continue;
                            
                            // Chỉ tạo cho clinic trong leave request
                            if (!scheduleClinicId.equals(clinicId)) continue;
                            
                            LocalTime startTime = schedule.getStartTime();
                            if (startTime == null) continue;
                            
                            String shiftType = WorkHoursConstants.determineShiftType(startTime);
                            // Chỉ xử lý MORNING và AFTERNOON, bỏ qua FULL_DAY
                            if ("FULL_DAY".equals(shiftType)) continue;
                            
                            // Tìm hoặc tạo attendance cho ca này (MORNING hoặc AFTERNOON)
                            java.util.Optional<sunshine_dental_care.entities.Attendance> existingAttendance = 
                                    attendanceRepository.findByUserIdAndClinicIdAndWorkDateAndShiftType(
                                            userId, clinicId, currentDate, shiftType);
                            
                            sunshine_dental_care.entities.Attendance attendance;
                            if (existingAttendance.isPresent()) {
                                attendance = existingAttendance.get();
                                if (attendance.getCheckInTime() == null) {
                                    attendance.setAttendanceStatus("APPROVED_ABSENCE");
                                    attendance.setShiftType(shiftType); // Đảm bảo shiftType đúng (MORNING hoặc AFTERNOON)
                                    attendanceRepository.save(attendance);
                                    totalCreated++;
                                }
                            } else {
                                attendance = new sunshine_dental_care.entities.Attendance();
                                attendance.setUserId(userId);
                                attendance.setClinicId(clinicId);
                                attendance.setWorkDate(currentDate);
                                attendance.setShiftType(shiftType); // MORNING hoặc AFTERNOON
                                attendance.setAttendanceStatus("APPROVED_ABSENCE");
                                attendance.setCreatedAt(Instant.now());
                                attendance.setUpdatedAt(Instant.now());
                                attendanceRepository.save(attendance);
                                totalCreated++;
                            }
                        }
                    }
                    // Nếu không có schedule, không tạo attendance record (bác sĩ không có lịch thì không cần attendance)
                }
            } else {
                // Nhân viên thường: nghỉ cả ngày (FULL_DAY)
                java.util.Optional<sunshine_dental_care.entities.Attendance> existingAttendance = 
                        attendanceRepository.findByUserIdAndClinicIdAndWorkDate(userId, clinicId, currentDate);
                
                sunshine_dental_care.entities.Attendance attendance;
                if (existingAttendance.isPresent()) {
                    attendance = existingAttendance.get();
                    if (attendance.getCheckInTime() == null) {
                        attendance.setAttendanceStatus("APPROVED_ABSENCE");
                        if (attendance.getShiftType() == null) {
                            attendance.setShiftType("FULL_DAY");
                        }
                        attendanceRepository.save(attendance);
                        totalCreated++;
                    }
                } else {
                    attendance = new sunshine_dental_care.entities.Attendance();
                    attendance.setUserId(userId);
                    attendance.setClinicId(clinicId);
                    attendance.setWorkDate(currentDate);
                    attendance.setShiftType("FULL_DAY");
                    attendance.setAttendanceStatus("APPROVED_ABSENCE");
                    attendance.setCreatedAt(Instant.now());
                    attendance.setUpdatedAt(Instant.now());
                    attendanceRepository.save(attendance);
                    totalCreated++;
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        log.info("Created/updated {} attendance records with APPROVED_ABSENCE for user {} from {} to {} (shiftType: {})",
                totalCreated, userId, startDate, endDate, leaveShiftType != null ? leaveShiftType : "FULL_DAY");
    }

    // Chuyển Entity LeaveRequest sang DTO trả về
    private LeaveRequestResponse mapToResponse(LeaveRequest leaveRequest) {
        if (leaveRequest == null) {
            throw new IllegalArgumentException("LeaveRequest cannot be null");
        }

        LeaveRequestResponse response = new LeaveRequestResponse();
        response.setId(leaveRequest.getId());

        response.setUserId(leaveRequest.getUser().getId());
        response.setUserName(leaveRequest.getUser().getUsername());
        response.setUserFullName(leaveRequest.getUser().getFullName());

        // Gán role cho user trong response
        try {
            List<UserRole> roles = leaveRequest.getUser().getUserRoles();
            if (roles != null && !roles.isEmpty()) {
                String roleNames = roles.stream()
                        .filter(ur -> ur.getIsActive() == null || ur.getIsActive())
                        .map(ur -> ur.getRole().getRoleName())
                        .collect(Collectors.joining(", "));
                response.setUserRole(roleNames);
            }
        } catch (Exception e) {
            log.warn("Could not fetch roles for user {}: {}", leaveRequest.getUser().getId(), e.getMessage());
        }

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

        // Tính số ngày nghỉ trong tháng hiện tại từ bảng Attendance (không tính Chủ nhật)
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        Long leaveDaysThisMonth = attendanceRepository.countLeaveDaysInMonthExcludingSunday(
                leaveRequest.getUser().getId(), currentYear, currentMonth);
        response.setLeaveBalance(leaveDaysThisMonth != null ? leaveDaysThisMonth.intValue() : 0);

        // Tính nghỉ phép năm: Tổng 12 ngày, đã dùng, còn lại
        int annualLeaveTotal = 12; // Tổng số phép năm
        Integer annualLeaveUsed = leaveRequestRepo.countApprovedLeaveDays(
                leaveRequest.getUser().getId(), currentYear);
        if (annualLeaveUsed == null) {
            annualLeaveUsed = 0;
        }
        int annualLeaveRemaining = Math.max(0, annualLeaveTotal - annualLeaveUsed);
        
        response.setAnnualLeaveTotal(annualLeaveTotal);
        response.setAnnualLeaveUsed(annualLeaveUsed);
        response.setAnnualLeaveRemaining(annualLeaveRemaining);

        // Tìm người thay thế tiềm năng (bác sĩ khác cùng ca)
        findPotentialReplacements(leaveRequest, response);

        return response;
    }

    // Tìm nhân sự thay thế bác sĩ nghỉ (lấy theo ca làm cùng ngày và trạng thái ACTIVE)
    private void findPotentialReplacements(LeaveRequest leaveRequest, LeaveRequestResponse response) {
        Integer userId = leaveRequest.getUser().getId();
        Integer clinicId = leaveRequest.getClinic().getId();
        LocalDate checkDate = leaveRequest.getStartDate();

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        List<String> replacements = new java.util.ArrayList<>();

        if (isDoctor) {
            List<DoctorSchedule> schedules = doctorScheduleRepo.findByClinicAndDate(clinicId, checkDate);
            String requestShift = leaveRequest.getShiftType();

            for (DoctorSchedule schedule : schedules) {
                if (schedule.getDoctor().getId().equals(userId)) continue;
                if (!"ACTIVE".equals(schedule.getStatus())) continue;

                boolean isMatch = false;
                if (requestShift == null || "FULL_DAY".equals(requestShift)) {
                    isMatch = true;
                } else {
                    boolean isMorningSchedule = schedule.getStartTime().isBefore(LocalTime.of(11, 0));
                    if ("MORNING".equals(requestShift) && isMorningSchedule)
                        isMatch = true;
                    else if ("AFTERNOON".equals(requestShift) && !isMorningSchedule)
                        isMatch = true;
                }

                if (isMatch) {
                    String docName = schedule.getDoctor().getFullName();
                    if (!replacements.contains(docName)) {
                        replacements.add(docName);
                    }
                }
            }
        }

        response.setPotentialReplacements(replacements);
        response.setReplacementAvailable(!replacements.isEmpty());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> getApprovedLeaveRequestsInDateRange(LocalDate startDate, LocalDate endDate) {
        List<LeaveRequest> leaveRequests = leaveRequestRepo.findApprovedByDateRange(startDate, endDate);        
        return leaveRequests.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Lấy danh sách userId của HR
    private List<Integer> getHrUserIds() {
        try {
            return userRoleRepo.findUserIdsByRoleName("HR");
        } catch (Exception e) {
            log.error("Failed to get HR user IDs: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
