package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import sunshine_dental_care.entities.User;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceNotFoundException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.services.impl.notification.NotificationService;
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

    // Lấy danh sách các attendance cần giải trình cho user (trong 30 ngày)
    public List<AttendanceExplanationResponse> getAttendanceNeedingExplanation(Integer userId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        List<Attendance> attendances = attendanceRepo.findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
                userId, startDate, endDate);

        return attendances.stream()
                .filter(this::needsExplanation)
                .map(this::mapToExplanationResponse)
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
        // Duyệt giải trình thiếu giờ check-out (có thể cần tự động gán giờ check-out)
        if ("MISSING_CHECK_OUT".equalsIgnoreCase(explanationType) && attendance.getCheckInTime() != null) {
            if (attendance.getCheckOutTime() == null) {
                LocalDate workDate = attendance.getWorkDate();
                LocalTime checkOutLocalTime = (customTime != null) ? customTime : WorkHoursConstants.EMPLOYEE_END_TIME;
                Instant checkOutTime = workDate.atTime(checkOutLocalTime)
                        .atZone(ZoneId.systemDefault())
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
        // Nếu đủ CI-CO nhưng chưa tính lại công thì thực hiện tính lại công
        if (!hasRecalculated && attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
            recalculateWorkHours(attendance, attendance.getCheckOutTime());
        }

        // Gửi thông báo duyệt giải trình cho nhân viên
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

    // Tính lại giờ công khi có cộng thêm số phút bù từ giải trình (dùng cho duyệt đi trễ, về sớm)
    private void recalculateWorkHoursWithCredit(Attendance attendance, long creditedMinutes) {
        if (attendance.getCheckInTime() == null || attendance.getCheckOutTime() == null) {
            return;
        }
        LocalTime checkInLocalTime = attendance.getCheckInTime()
                .atZone(ZoneId.systemDefault())
                .toLocalTime();
        LocalTime checkOutLocalTime = attendance.getCheckOutTime()
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), attendance.getCheckOutTime())
                .toMinutes();

        // Xác định có phải bác sĩ không dựa vào shiftType (MORNING/AFTERNOON = bác sĩ, FULL_DAY/null = nhân viên)
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

        attendance.setExpectedWorkHours(BigDecimal.valueOf(WorkHoursConstants.EMPLOYEE_EXPECTED_HOURS));
    }

    // Tính lại giờ công thông thường (áp dụng nếu đủ CI-CO)
    private void recalculateWorkHours(Attendance attendance, Instant checkOutTime) {
        if (attendance.getCheckInTime() == null || checkOutTime == null) {
            return;
        }
        LocalTime checkInLocalTime = attendance.getCheckInTime()
                .atZone(ZoneId.systemDefault())
                .toLocalTime();
        LocalTime checkOutLocalTime = checkOutTime
                .atZone(ZoneId.systemDefault())
                .toLocalTime();

        long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), checkOutTime).toMinutes();

        // Xác định có phải bác sĩ không dựa vào shiftType (MORNING/AFTERNOON = bác sĩ, FULL_DAY/null = nhân viên)
        String shiftType = attendance.getShiftType();
        boolean isDoctor = shiftType != null && 
                (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) || 
                 WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType));
        int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(
                checkInLocalTime, checkOutLocalTime, isDoctor);
        attendance.setLunchBreakMinutes(lunchBreakMinutes);

        long minutesLate = 0;
        String currentStatus = attendance.getAttendanceStatus();
        if (currentStatus != null && !"APPROVED_LATE".equals(currentStatus)) {
            Integer lateMinutesValue = attendance.getLateMinutes();
            minutesLate = (lateMinutesValue != null) ? lateMinutesValue : 0;
        }

        attendance.setEarlyMinutes(0);

        long adjustedMinutes = totalMinutes - lunchBreakMinutes;
        if (adjustedMinutes < 0) {
            adjustedMinutes = 0;
        }
        BigDecimal actualHours = BigDecimal.valueOf(adjustedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        attendance.setActualWorkHours(actualHours);

        attendance.setExpectedWorkHours(BigDecimal.valueOf(WorkHoursConstants.EMPLOYEE_EXPECTED_HOURS));
    }

    // Xác định trạng thái attendance sau khi được HR duyệt
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

    // Xử lý từ chối giải trình: cập nhật note và gửi thông báo cho nhân viên
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

        // Gửi thông báo từ chối giải trình đến user
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

    // Kiểm tra attendance có nằm trong nhóm các trường hợp cần giải trình không
    public boolean needsExplanation(Attendance att) {
        if (hasPendingExplanation(att) || hasProcessedExplanation(att)) {
            return false;
        }

        String status = att.getAttendanceStatus();
        boolean hasCheckIn = att.getCheckInTime() != null;
        boolean hasCheckOut = att.getCheckOutTime() != null;

        LocalDate today = LocalDate.now();
        boolean isToday = att.getWorkDate() != null && att.getWorkDate().equals(today);

        // Không yêu cầu giải trình nếu là ngày hiện tại và đã check-in nhưng chưa check-out
        if (isToday && hasCheckIn && !hasCheckOut) {
            return false;
        }

        return "LATE".equals(status) ||
                "ABSENT".equals(status) ||
                (hasCheckIn && !hasCheckOut) ||
                (!hasCheckIn && hasCheckOut);
    }

    // Kiểm tra attendance đang chờ xử lý giải trình hay không
    public boolean hasPendingExplanation(Attendance att) {
        String note = att.getNote();
        return note != null && note.contains("[EXPLANATION_REQUEST:");
    }

    // Kiểm tra attendance đã xử lý giải trình (duyệt/ từ chối) chưa
    private boolean hasProcessedExplanation(Attendance att) {
        String note = att.getNote();
        return note != null && (note.contains("[APPROVED]") || note.contains("[REJECTED]"));
    }

    // Trích xuất loại giải trình từ note lưu trên attendance
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

    // Trích xuất lý do giải trình của nhân viên từ note
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

    // Mapping Attendance sang DTO phản hồi giải trình
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

    // Đoán loại giải trình nếu chưa có note lưu giải trình
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
}
