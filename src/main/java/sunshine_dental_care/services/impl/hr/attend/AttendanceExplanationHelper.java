package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.AdminExplanationActionRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationResponse;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceNotFoundException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.hr.ShiftService;
import sunshine_dental_care.utils.WorkHoursConstants;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceExplanationHelper {

    private final AttendanceRepository attendanceRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final NotificationService notificationService;
    private final UserRoleRepo userRoleRepo;
    private final ShiftService shiftService;
    private final AttendanceCalculationHelper calculationHelper;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final sunshine_dental_care.services.impl.hr.attend.AttendanceStatusCalculator attendanceStatusCalculator;

    // Lấy danh sách các attendance cần giải trình cho user (chỉ tháng hiện tại)
    // Bao gồm cả từ attendance records và từ schedules
    public List<AttendanceExplanationResponse> getAttendanceNeedingExplanation(Integer userId) {
        LocalDate endDate = LocalDate.now();
        // Chỉ lấy từ đầu tháng hiện tại
        LocalDate startDate = endDate.withDayOfMonth(1);
        
        // 1. Lấy explanations từ attendance records đã có
        List<Attendance> attendances = attendanceRepo.findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
                userId, startDate, endDate);
        List<AttendanceExplanationResponse> fromAttendances = attendances.stream()
                .filter(this::needsExplanation)
                .map(this::mapToExplanationResponse)
                .collect(Collectors.toList());
        
        // 2. Lấy explanations từ schedules (chỉ cho bác sĩ có schedule nhưng chưa check-in)
        // Kiểm tra xem user có phải là bác sĩ không
        boolean isDoctor = false;
        try {
            List<sunshine_dental_care.entities.UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
            if (userRoles != null && !userRoles.isEmpty()) {
                isDoctor = userRoles.stream()
                        .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));
            }
        } catch (Exception e) {
            log.warn("Error checking if user {} is doctor: {}", userId, e.getMessage());
        }
        
        List<AttendanceExplanationResponse> fromSchedules = new java.util.ArrayList<>();
        if (isDoctor) {
            // Chỉ lấy từ schedules nếu là bác sĩ
            List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndDateRange(userId, startDate, endDate);
            fromSchedules = schedules.stream()
                .filter(schedule -> {
                    // Chỉ lấy schedule ACTIVE
                    if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                        return false;
                    }
                    // Kiểm tra xem đã có attendance record chưa
                    String shiftType = WorkHoursConstants.determineShiftType(schedule.getStartTime());
                    java.util.Optional<Attendance> existing = attendanceRepo.findByUserIdAndClinicIdAndWorkDateAndShiftType(
                            userId, schedule.getClinic().getId(), schedule.getWorkDate(), shiftType);
                    // Chỉ lấy nếu chưa có attendance record
                    return existing.isEmpty();
                })
                .filter(schedule -> {
                    // Kiểm tra xem đã qua giờ bắt đầu ca chưa
                    LocalDate workDate = schedule.getWorkDate();
                    LocalDate today = LocalDate.now();
                    LocalTime currentTime = LocalTime.now(WorkHoursConstants.VN_TIMEZONE);
                    LocalTime startTime = schedule.getStartTime();
                    
                    // Nếu là ngày hôm nay và chưa đến giờ bắt đầu ca → không hiển thị
                    if (workDate.equals(today) && currentTime.isBefore(startTime)) {
                        return false;
                    }
                    // Nếu là ngày trong quá khứ → hiển thị
                    if (workDate.isBefore(today)) {
                        return true;
                    }
                    // Nếu là ngày hôm nay và đã qua giờ bắt đầu ca → hiển thị
                    return workDate.equals(today) && !currentTime.isBefore(startTime);
                })
                .map(this::mapScheduleToExplanationResponse)
                .collect(Collectors.toList());
        }
        // Nhân viên không có schedule, chỉ lấy từ attendance records
        
        // 3. Merge và loại bỏ trùng lặp (theo workDate + shiftType)
        java.util.Map<String, AttendanceExplanationResponse> merged = new java.util.HashMap<>();
        fromAttendances.forEach(exp -> {
            String key = exp.getWorkDate() + "_" + exp.getShiftType();
            merged.put(key, exp);
        });
        fromSchedules.forEach(exp -> {
            String key = exp.getWorkDate() + "_" + exp.getShiftType();
            // Chỉ thêm nếu chưa có trong attendance records
            if (!merged.containsKey(key)) {
                merged.put(key, exp);
            }
        });
        
        // 4. Sắp xếp theo ngày giảm dần
        return merged.values().stream()
                .sorted((a, b) -> {
                    if (a.getWorkDate() == null && b.getWorkDate() == null) return 0;
                    if (a.getWorkDate() == null) return 1;
                    if (b.getWorkDate() == null) return -1;
                    return b.getWorkDate().compareTo(a.getWorkDate());
                })
                .collect(Collectors.toList());
    }

    // Nộp giải trình: tạo mới hoặc cập nhật bản ghi attendance với note giải trình
    public Attendance submitExplanation(AttendanceExplanationRequest request, Integer userId) {
        Attendance attendance;
        if (request.getAttendanceId() == null || request.getAttendanceId() == 0) {
            if (request.getClinicId() == null || request.getWorkDate() == null || request.getShiftType() == null) {
                throw new AttendanceValidationException(
                        "Missing required information to create attendance: clinicId, workDate, and shiftType are required when attendanceId is 0");
            }
            java.util.Optional<Attendance> existing = attendanceRepo
                    .findByUserIdAndClinicIdAndWorkDateAndShiftType(
                            userId, request.getClinicId(), request.getWorkDate(), request.getShiftType());

            if (existing.isPresent()) {
                attendance = existing.get();
            } else {
                attendance = new Attendance();
                attendance.setUserId(userId);
                attendance.setClinicId(request.getClinicId());
                attendance.setWorkDate(request.getWorkDate());
                attendance.setShiftType(request.getShiftType());
                attendance.setAttendanceStatus("ABSENT");
                attendance = attendanceRepo.save(attendance);
                log.info(
                        "Created new attendance record (id={}) for explanation submission: userId={}, clinicId={}, workDate={}, shiftType={}",
                        attendance.getId(), userId, request.getClinicId(), request.getWorkDate(), request.getShiftType());
            }
        } else {
            attendance = attendanceRepo.findById(request.getAttendanceId())
                    .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));
        }

        // Validation: Không cho phép gửi giải trình MISSING_CHECK_OUT nếu đã có checkOutTime
        if ("MISSING_CHECK_OUT".equalsIgnoreCase(request.getExplanationType()) 
                && attendance.getCheckOutTime() != null) {
            throw new AttendanceValidationException(
                    "Cannot submit MISSING_CHECK_OUT explanation: check-out time already exists");
        }

        String note = String.format("[EXPLANATION_REQUEST:%s] %s",
                request.getExplanationType().toUpperCase(), request.getReason());
        if (attendance.getNote() != null && !attendance.getNote().trim().isEmpty()) {
            attendance.setNote(attendance.getNote() + "\n" + note);
        } else {
            attendance.setNote(note);
        }

        attendance = attendanceRepo.save(attendance);

        // Gửi notification realtime đến tất cả user HR khi có giải trình mới
        try {
            User employee = userRepo.findById(userId).orElse(null);
            String employeeName = employee != null && employee.getFullName() != null ? employee.getFullName() : "Nhân viên";
            Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
            String clinicName = clinic != null && clinic.getClinicName() != null ? clinic.getClinicName() : "";

            String message = String.format("%s đã gửi giải trình cho ngày %s",
                    employeeName, attendance.getWorkDate());
            if (!clinicName.isEmpty()) {
                message += String.format(" tại %s", clinicName);
            }

            List<Integer> hrUserIds = userRoleRepo.findUserIdsByRoleName("HR");
            log.info("Sending EXPLANATION_SUBMITTED notification to {} HR users for attendanceId={}, userId={}",
                    hrUserIds.size(), attendance.getId(), userId);

            // Gửi notification đến từng HR user
            for (Integer hrUserId : hrUserIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(hrUserId)
                            .type("EXPLANATION_SUBMITTED")
                            .priority("MEDIUM")
                            .title("Giải trình mới cần xử lý")
                            .message(message)
                            .relatedEntityType("ATTENDANCE")
                            .relatedEntityId(attendance.getId())
                            .build();
                    notificationService.sendNotification(notiRequest);
                    log.debug("EXPLANATION_SUBMITTED notification sent to HR userId={} for attendanceId={}",
                            hrUserId, attendance.getId());
                } catch (Exception e) {
                    log.error("Failed to send EXPLANATION_SUBMITTED notification to HR userId={} for attendanceId={}: {}",
                            hrUserId, attendance.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send EXPLANATION_SUBMITTED notifications for attendanceId={}, userId={}: {}",
                    attendance.getId(), userId, e.getMessage());
            // Không ném exception để tránh rollback transaction
        }

        return attendance;
    }

    // Lấy danh sách các bản ghi attendance đang chờ HR duyệt giải trình
    public List<AttendanceExplanationResponse> getPendingExplanations(Integer clinicId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<Attendance> attendances = clinicId != null
                ? attendanceRepo.findByClinicIdAndWorkDateBetween(clinicId, startDate, endDate, PageRequest.of(0, 1000)).getContent()
                : attendanceRepo.findByWorkDateBetween(startDate, endDate);

        return attendances.stream()
                .filter(this::hasPendingExplanation)
                .map(this::mapToExplanationResponse)
                .collect(Collectors.toList());
    }

    // Duyệt hoặc từ chối giải trình từ phía HR
    public Attendance processExplanation(AdminExplanationActionRequest request, Integer adminUserId) {
        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        log.info("Processing explanation for attendanceId={}, userId={}, workDate={}, shiftType={}",
                attendance.getId(), attendance.getUserId(), attendance.getWorkDate(), attendance.getShiftType());

        String note = attendance.getNote();
        if (note == null || !note.contains("[EXPLANATION_REQUEST:")) {
            throw new AttendanceValidationException("No pending explanation found for this attendance");
        }

        String explanationType = extractExplanationType(note);
        String employeeReason = extractEmployeeReason(note);
        User hrUser = userRepo.findById(adminUserId)
                .orElseThrow(() -> new AttendanceNotFoundException("HR user not found"));
        String hrUserName = hrUser.getFullName() != null ? hrUser.getFullName() : "HR";

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            LocalTime customTime = null;
            if (request.getCustomTime() != null && !request.getCustomTime().trim().isEmpty()) {
                try {
                    customTime = LocalTime.parse(request.getCustomTime().trim());
                } catch (Exception e) {
                    // Không cần thông báo warn
                }
            }
            processApproval(attendance, note, explanationType, employeeReason, hrUserName, request.getAdminNote(),
                    customTime);
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            processRejection(attendance, note, employeeReason, hrUserName, request.getAdminNote());
        } else {
            throw new AttendanceValidationException("Invalid action. Must be APPROVE or REJECT");
        }

        return attendanceRepo.save(attendance);
    }

    // Duyệt giải trình HR: cập nhật trạng thái, tính lại giờ công, gửi thông báo
    private void processApproval(Attendance attendance, String note, String explanationType,
                                String employeeReason, String hrUserName, String adminNote, LocalTime customTime) {
        String approvedNote = String.format("[APPROVED] %s", employeeReason);
        if (adminNote != null && !adminNote.trim().isEmpty()) {
            approvedNote += String.format(" | [HR: %s] %s", hrUserName, adminNote);
        }
        note = note.replaceFirst("\\[EXPLANATION_REQUEST:[^\\]]+\\].*", approvedNote);
        attendance.setNote(note);

        String oldStatus = attendance.getAttendanceStatus();
        String newStatus = determineApprovedStatus(explanationType, oldStatus);

        if (newStatus != null && !newStatus.equals(oldStatus)) {
            attendance.setAttendanceStatus(newStatus);
        }

        boolean hasRecalculated = false;
        // Duyệt giải trình đi trễ: cộng lại số phút đi trễ
        if ("LATE".equalsIgnoreCase(explanationType) && "APPROVED_LATE".equals(newStatus)) {
            Integer oldLateMinutes = attendance.getLateMinutes();
            long credit = oldLateMinutes != null ? oldLateMinutes : 0;
            attendance.setLateMinutes(0);
            if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                recalculateWorkHoursWithCredit(attendance, credit);
                hasRecalculated = true;
            }
        }
        // Duyệt giải trình về sớm: cộng lại số phút về sớm
        if ("EARLY_CHECKOUT".equalsIgnoreCase(explanationType) && "APPROVED_EARLY_LEAVE".equals(newStatus)) {
            Integer oldEarlyMinutes = attendance.getEarlyMinutes();
            long credit = oldEarlyMinutes != null ? oldEarlyMinutes : 0;
            attendance.setEarlyMinutes(0);
            if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                recalculateWorkHoursWithCredit(attendance, credit);
                hasRecalculated = true;
            }
        }
        // Nếu duyệt thiếu CI-CO, tự động gán giờ checkout nếu cần
        if ("MISSING_CHECK_OUT".equalsIgnoreCase(explanationType) && attendance.getCheckInTime() != null) {
            if (attendance.getCheckOutTime() == null) {
                LocalDate workDate = attendance.getWorkDate();
                LocalTime checkOutLocalTime;
                if (customTime != null) {
                    checkOutLocalTime = customTime;
                } else {
                    // Lấy expectedEndTime theo ca của bác sĩ (MORNING: 11:00, AFTERNOON: 18:00)
                    String shiftType = attendance.getShiftType();
                    boolean isDoctor = shiftType != null &&
                            (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) ||
                             WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType));
                    if (isDoctor && shiftType != null) {
                        java.util.Optional<sunshine_dental_care.entities.DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                                attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate(), shiftType);
                        sunshine_dental_care.entities.DoctorSchedule schedule = matchedSchedule.orElse(null);
                        checkOutLocalTime = shiftService.getExpectedEndTime(shiftType, schedule);
                    } else {
                        // Nhân viên: dùng giờ kết thúc mặc định
                        checkOutLocalTime = WorkHoursConstants.EMPLOYEE_END_TIME;
                    }
                }
                Instant checkOutTime = workDate.atTime(checkOutLocalTime)
                        .atZone(WorkHoursConstants.VN_TIMEZONE)
                        .toInstant();
                attendance.setCheckOutTime(checkOutTime);
            }
            String finalStatus = attendance.getAttendanceStatus();
            if ("APPROVED_LATE".equals(finalStatus)) {
                Integer oldLateMinutes = attendance.getLateMinutes();
                long credit = oldLateMinutes != null ? oldLateMinutes : 0;
                attendance.setLateMinutes(0);
                recalculateWorkHoursWithCredit(attendance, credit);
                hasRecalculated = true;
            } else {
                if (attendance.getCheckOutTime() != null) {
                    recalculateWorkHours(attendance, attendance.getCheckOutTime());
                    hasRecalculated = true;
                }
            }
        }
        // Nếu đủ CI/CO nhưng chưa tính lại công thì thực hiện tính lại công
        // Không tính lại nếu status là APPROVED_ABSENCE hoặc ABSENT
        String finalStatus = attendance.getAttendanceStatus();
        if (!hasRecalculated && attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null
                && !"ABSENT".equals(finalStatus) && !"APPROVED_ABSENCE".equals(finalStatus)) {
            recalculateWorkHours(attendance, attendance.getCheckOutTime());
        } else if ("ABSENT".equals(finalStatus) || "APPROVED_ABSENCE".equals(finalStatus)) {
            // Đảm bảo actualWorkHours = 0 cho ABSENT/APPROVED_ABSENCE
            attendance.setActualWorkHours(java.math.BigDecimal.ZERO);
            log.info("Setting actualWorkHours to 0 for status {} after explanation approval", finalStatus);
        }

        // Gửi notification duyệt giải trình cho nhân viên
        try {
            log.info("Sending approval notification to userId={} for attendanceId={}, workDate={}, shiftType={}",
                    attendance.getUserId(), attendance.getId(), attendance.getWorkDate(), attendance.getShiftType());

            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(attendance.getUserId())
                    .type("EXPLANATION_APPROVED")
                    .priority("MEDIUM")
                    .title("Explanation Approved")
                    .message(String.format("Your explanation for %s has been approved by %s.",
                            attendance.getWorkDate(), hrUserName))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendance.getId())
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send approval notification for attendanceId={}, userId={}: {}",
                    attendance.getId(), attendance.getUserId(), e.getMessage());
        }
    }

    // Tính lại giờ công khi duyệt giải trình được cộng số phút bù
    private void recalculateWorkHoursWithCredit(Attendance attendance, long creditedMinutes) {
        if (attendance.getCheckInTime() == null || attendance.getCheckOutTime() == null) {
            return;
        }
        
        // Không tính giờ làm nếu status là ABSENT hoặc APPROVED_ABSENCE
        String currentStatus = attendance.getAttendanceStatus();
        if ("ABSENT".equals(currentStatus) || "APPROVED_ABSENCE".equals(currentStatus)) {
            attendance.setActualWorkHours(java.math.BigDecimal.ZERO);
            log.info("Skipping work hours calculation for status {} - setting actualWorkHours to 0", currentStatus);
            return;
        }
        
        LocalTime checkInLocalTime = attendance.getCheckInTime()
                .atZone(WorkHoursConstants.VN_TIMEZONE)
                .toLocalTime();
        LocalTime checkOutLocalTime = attendance.getCheckOutTime()
                .atZone(WorkHoursConstants.VN_TIMEZONE)
                .toLocalTime();

        long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), attendance.getCheckOutTime())
                .toMinutes();

        String shiftType = attendance.getShiftType();
        boolean isDoctor = shiftType != null &&
                (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) ||
                 WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType));
        int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(
                checkInLocalTime, checkOutLocalTime, isDoctor);
        attendance.setLunchBreakMinutes(lunchBreakMinutes);

        attendance.setLateMinutes(0);
        attendance.setEarlyMinutes(0);

        long adjustedMinutes = (totalMinutes - lunchBreakMinutes) + creditedMinutes;
        log.info(
                "RecalculateWorkHoursWithCredit: totalMinutes={}, lunchBreakMinutes={}, creditedMinutes={}, adjustedMinutes={}",
                totalMinutes, lunchBreakMinutes, creditedMinutes, adjustedMinutes);

        if (adjustedMinutes < 0) {
            adjustedMinutes = 0;
        }
        BigDecimal actualHours = BigDecimal.valueOf(adjustedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        attendance.setActualWorkHours(actualHours);

        // Tính expectedWorkHours dựa trên shiftType (bác sĩ làm theo ca)
        BigDecimal expectedWorkHours = calculateExpectedWorkHours(attendance);
        attendance.setExpectedWorkHours(expectedWorkHours);
    }

    // Tính lại giờ công (không cộng bù phút)
    private void recalculateWorkHours(Attendance attendance, Instant checkOutTime) {
        if (attendance.getCheckInTime() == null || checkOutTime == null) {
            return;
        }
        
        // Không tính giờ làm nếu status là ABSENT hoặc APPROVED_ABSENCE
        String currentStatus = attendance.getAttendanceStatus();
        if ("ABSENT".equals(currentStatus) || "APPROVED_ABSENCE".equals(currentStatus)) {
            attendance.setActualWorkHours(java.math.BigDecimal.ZERO);
            log.info("Skipping work hours calculation for status {} - setting actualWorkHours to 0", currentStatus);
            return;
        }
        
        LocalTime checkInLocalTime = attendance.getCheckInTime()
                .atZone(WorkHoursConstants.VN_TIMEZONE)
                .toLocalTime();
        LocalTime checkOutLocalTime = checkOutTime
                .atZone(WorkHoursConstants.VN_TIMEZONE)
                .toLocalTime();

        long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), checkOutTime).toMinutes();

        String shiftType = attendance.getShiftType();
        boolean isDoctor = shiftType != null &&
                (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) ||
                 WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType));
        int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(
                checkInLocalTime, checkOutLocalTime, isDoctor);
        attendance.setLunchBreakMinutes(lunchBreakMinutes);

        // Tính earlyMinutes dựa trên expectedEndTime
        LocalTime expectedEndTime = WorkHoursConstants.EMPLOYEE_END_TIME;
        if (isDoctor && shiftType != null) {
            java.util.Optional<sunshine_dental_care.entities.DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate(), shiftType);
            sunshine_dental_care.entities.DoctorSchedule schedule = matchedSchedule.orElse(null);
            expectedEndTime = shiftService.getExpectedEndTime(shiftType, schedule);
        }
        attendance.setEarlyMinutes(calculationHelper.calculateEarlyMinutes(checkOutLocalTime, expectedEndTime));

        long adjustedMinutes = totalMinutes - lunchBreakMinutes;
        if (adjustedMinutes < 0) {
            adjustedMinutes = 0;
        }
        BigDecimal actualHours = BigDecimal.valueOf(adjustedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        attendance.setActualWorkHours(actualHours);

        // Tính expectedWorkHours dựa trên shiftType (bác sĩ làm theo ca)
        BigDecimal expectedWorkHours = calculateExpectedWorkHours(attendance);
        attendance.setExpectedWorkHours(expectedWorkHours);
    }

    // Tính expectedWorkHours dựa trên shiftType và schedule của bác sĩ
    private BigDecimal calculateExpectedWorkHours(Attendance attendance) {
        String shiftType = attendance.getShiftType();
        boolean isDoctor = shiftType != null &&
                (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) ||
                 WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType));

        if (isDoctor && shiftType != null) {
            // Tìm schedule phù hợp để lấy startTime và endTime chính xác
            java.util.Optional<sunshine_dental_care.entities.DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate(), shiftType);
            sunshine_dental_care.entities.DoctorSchedule schedule = matchedSchedule.orElse(null);
            
            LocalTime expectedStartTime = shiftService.getExpectedStartTime(shiftType, schedule);
            LocalTime expectedEndTime = shiftService.getExpectedEndTime(shiftType, schedule);
            return calculationHelper.calculateExpectedWorkHours(expectedStartTime, expectedEndTime);
        } else {
            // Nhân viên làm cả ngày: 8 giờ
            return BigDecimal.valueOf(WorkHoursConstants.EMPLOYEE_EXPECTED_HOURS);
        }
    }

    // Xác định trạng thái attendance sau khi HR duyệt giải trình dựa theo loại giải trình
    private String determineApprovedStatus(String explanationType, String oldStatus) {
        if ("LATE".equalsIgnoreCase(explanationType)) {
            return "APPROVED_LATE";
        } else if ("EARLY_CHECKOUT".equalsIgnoreCase(explanationType)) {
            return "APPROVED_EARLY_LEAVE";
        } else if ("ABSENT".equalsIgnoreCase(explanationType)) {
            return "APPROVED_ABSENCE";
        } else if ("MISSING_CHECK_IN".equalsIgnoreCase(explanationType)) {
            return "APPROVED_PRESENT";
        } else if ("MISSING_CHECK_OUT".equalsIgnoreCase(explanationType)) {
            if ("LATE".equalsIgnoreCase(oldStatus)) {
                return "APPROVED_LATE";
            } else if ("ON_TIME".equalsIgnoreCase(oldStatus)) {
                return "APPROVED_PRESENT";
            }
            return oldStatus;
        }
        return oldStatus;
    }

    // Xử lý từ chối giải trình: cập nhật note và gửi notification về cho user
    private void processRejection(Attendance attendance, String note, String employeeReason,
                                  String hrUserName, String adminNote) {
        String rejectedNote = String.format("[REJECTED] %s", employeeReason);
        if (adminNote != null && !adminNote.trim().isEmpty()) {
            rejectedNote += String.format(" | [HR: %s] %s", hrUserName, adminNote);
        } else {
            rejectedNote += String.format(" | [HR: %s] Explanation rejected", hrUserName);
        }
        note = note.replaceFirst("\\[EXPLANATION_REQUEST:[^\\]]+\\].*", rejectedNote);
        note = note.replaceFirst("\\[EXPLANATION_REQUEST:[^\\]]+\\].*", rejectedNote);
        attendance.setNote(note);

        // Gửi notification từ chối giải trình cho user
        try {
            log.info("Sending rejection notification to userId={} for attendanceId={}, workDate={}, shiftType={}",
                    attendance.getUserId(), attendance.getId(), attendance.getWorkDate(), attendance.getShiftType());

            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(attendance.getUserId())
                    .type("EXPLANATION_REJECTED")
                    .priority("MEDIUM")
                    .title("Explanation Rejected")
                    .message(String.format("Your explanation for %s has been rejected by %s.",
                            attendance.getWorkDate(), hrUserName))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendance.getId())
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send rejection notification for attendanceId={}, userId={}: {}",
                    attendance.getId(), attendance.getUserId(), e.getMessage());
        }
    }

    // Kiểm tra attendance có cần giải trình không
    public boolean needsExplanation(Attendance att) {
        if (hasPendingExplanation(att)) {
            return false;
        }

        String status = att.getAttendanceStatus();
        boolean hasCheckIn = att.getCheckInTime() != null;
        boolean hasCheckOut = att.getCheckOutTime() != null;

        LocalDate today = LocalDate.now();
        boolean isToday = att.getWorkDate() != null && att.getWorkDate().equals(today);
        LocalTime currentTime = LocalTime.now(WorkHoursConstants.VN_TIMEZONE);

        // Nếu là ngày hôm nay và chưa đến giờ bắt đầu ca thì không hiển thị giải trình
        // (trừ trường hợp LATE vì đã check-in rồi)
        if (isToday && !"LATE".equals(status)) {
            String shiftType = att.getShiftType();
            LocalTime shiftStartTime = null;
            
            // Xác định giờ bắt đầu ca
            if (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType)) {
                // Bác sĩ ca sáng: 8:00
                shiftStartTime = WorkHoursConstants.MORNING_SHIFT_START;
            } else if (WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
                // Bác sĩ ca chiều: 13:00
                shiftStartTime = WorkHoursConstants.AFTERNOON_SHIFT_START;
            } else if (WorkHoursConstants.SHIFT_TYPE_FULL_DAY.equals(shiftType) || shiftType == null) {
                // Nhân viên làm cả ngày: 8:00
                shiftStartTime = WorkHoursConstants.EMPLOYEE_START_TIME;
            }
            
            // Nếu chưa đến giờ bắt đầu ca → không hiển thị giải trình
            // (kể cả khi status = ABSENT, vì chưa đến giờ ca nên chưa thể xác định vắng mặt)
            if (shiftStartTime != null && currentTime.isBefore(shiftStartTime)) {
                return false;
            }
        }

        // Không bắt giải trình nếu là ngày hiện tại và đã checkin nhưng chưa checkout
        if (isToday && hasCheckIn && !hasCheckOut) {
            return false;
        }

        // Trường hợp đặc biệt: cho phép tạo giải trình MISSING_CHECK_OUT sau khi đã có APPROVED_LATE và đã xử lý (APPROVED/REJECTED)
        boolean isApprovedLate = "APPROVED_LATE".equals(status);
        boolean needsMissingCheckOut = hasCheckIn && !hasCheckOut;

        if (isApprovedLate && needsMissingCheckOut) {
            if (hasProcessedExplanation(att)) {
                return true;
            }
        }

        // Nếu đã có giải trình đã xử lý --> không cho tạo giải trình mới
        if (hasProcessedExplanation(att)) {
            return false;
        }

        return "LATE".equals(status) ||
                "ABSENT".equals(status) ||
                (hasCheckIn && !hasCheckOut) ||
                (!hasCheckIn && hasCheckOut);
    }
    
    // Kiểm tra attendance đang ở trạng thái chờ giải trình (chưa HR xử lý)
    public boolean hasPendingExplanation(Attendance att) {
        String note = att.getNote();
        return note != null && note.contains("[EXPLANATION_REQUEST:");
    }

    // Kiểm tra attendance đã được HR duyệt/từ chối giải trình chưa
    private boolean hasProcessedExplanation(Attendance att) {
        String note = att.getNote();
        return note != null && (note.contains("[APPROVED]") || note.contains("[REJECTED]"));
    }

    // Trích xuất loại giải trình từ note trên attendance
    public String extractExplanationType(String note) {
        if (note == null)
            return null;
        Pattern pattern = Pattern.compile("\\[EXPLANATION_REQUEST:([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(note);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Trích xuất lý do giải trình từ note
    public String extractEmployeeReason(String note) {
        if (note == null)
            return null;
        Pattern pattern = Pattern.compile("\\[EXPLANATION_REQUEST:[^\\]]+\\]\\s*(.*?)(?:\\s*\\|\\s*\\[Admin:.*)?$");
        Matcher matcher = pattern.matcher(note);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return note;
    }

    // Mapping attendance sang DTO phản hồi
    public AttendanceExplanationResponse mapToExplanationResponse(Attendance attendance) {
        AttendanceExplanationResponse response = new AttendanceExplanationResponse();
        response.setAttendanceId(attendance.getId());
        response.setUserId(attendance.getUserId());
        response.setClinicId(attendance.getClinicId());
        response.setWorkDate(attendance.getWorkDate());
        response.setCheckInTime(attendance.getCheckInTime());
        response.setCheckOutTime(attendance.getCheckOutTime());
        response.setAttendanceStatus(attendance.getAttendanceStatus());
        response.setNote(attendance.getNote());
        response.setShiftType(attendance.getShiftType());

        String note = attendance.getNote();
        if (note != null) {
            String explanationType = extractExplanationType(note);
            if (explanationType != null) {
                response.setExplanationType(explanationType);
                response.setEmployeeReason(extractEmployeeReason(note));
                if (note.contains("[APPROVED]")) {
                    response.setExplanationStatus("APPROVED");
                } else if (note.contains("[REJECTED]")) {
                    response.setExplanationStatus("REJECTED");
                } else {
                    response.setExplanationStatus("PENDING");
                }
            } else {
                setExplanationTypeFromStatus(response, attendance);
                response.setExplanationStatus("PENDING");
            }
        } else {
            setExplanationTypeFromStatus(response, attendance);
            response.setExplanationStatus("PENDING");
        }

        User user = userRepo.findById(attendance.getUserId()).orElse(null);
        Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
        if (user != null) {
            response.setUserName(user.getFullName());
        }
        if (clinic != null) {
            response.setClinicName(clinic.getClinicName());
        }

        return response;
    }

    // Suy luận loại giải trình dựa vào trạng thái nếu chưa có note giải trình
    private void setExplanationTypeFromStatus(AttendanceExplanationResponse response, Attendance attendance) {
        if ("LATE".equals(attendance.getAttendanceStatus())) {
            response.setExplanationType("LATE");
        } else if ("ABSENT".equals(attendance.getAttendanceStatus())) {
            response.setExplanationType("ABSENT");
        } else if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() == null) {
            response.setExplanationType("MISSING_CHECK_OUT");
        } else if (attendance.getCheckInTime() == null && attendance.getCheckOutTime() != null) {
            response.setExplanationType("MISSING_CHECK_IN");
        }
    }

    // Mapping schedule sang DTO phản hồi (cho trường hợp có schedule nhưng chưa check-in)
    private AttendanceExplanationResponse mapScheduleToExplanationResponse(DoctorSchedule schedule) {
        AttendanceExplanationResponse response = new AttendanceExplanationResponse();
        response.setAttendanceId(0); // Chưa có attendance record
        response.setUserId(schedule.getDoctor().getId());
        response.setClinicId(schedule.getClinic().getId());
        response.setWorkDate(schedule.getWorkDate());
        response.setCheckInTime(null); // Chưa check-in
        response.setCheckOutTime(null); // Chưa check-out
        response.setAttendanceStatus("ABSENT"); // Mặc định là ABSENT vì chưa check-in
        response.setNote(null);
        
        // Xác định shiftType từ startTime
        String shiftType = WorkHoursConstants.determineShiftType(schedule.getStartTime());
        response.setShiftType(shiftType);
        
        // Set explanation type là ABSENT
        response.setExplanationType("ABSENT");
        response.setExplanationStatus("PENDING");
        response.setEmployeeReason(null);
        
        // Set user và clinic name
        if (schedule.getDoctor() != null) {
            response.setUserName(schedule.getDoctor().getFullName());
        }
        if (schedule.getClinic() != null) {
            response.setClinicName(schedule.getClinic().getClinicName());
        }
        
        return response;
    }
}
