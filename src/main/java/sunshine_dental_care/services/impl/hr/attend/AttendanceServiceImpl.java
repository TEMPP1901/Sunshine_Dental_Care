package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.AdminExplanationActionRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckInRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckOutRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceExplanationResponse;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.DailySummaryResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlySummaryResponse;
import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceNotFoundException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.impl.hr.ClinicResolutionService;
import sunshine_dental_care.services.impl.hr.attend.AttendanceVerificationService.VerificationResult;
import sunshine_dental_care.services.impl.hr.wifi.WiFiHelperService;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService.FaceVerificationResult;
import sunshine_dental_care.services.interfaces.hr.ShiftService;
import sunshine_dental_care.services.interfaces.system.AuditLogService;
import sunshine_dental_care.utils.WorkHoursConstants;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final AttendanceVerificationService attendanceVerificationService;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    private final AttendanceStatusCalculator attendanceStatusCalculator;
    private final AttendanceReportService attendanceReportService;
    private final UserRoleRepo userRoleRepo;
    private final ClinicResolutionService clinicResolutionService;
    private final WiFiHelperService wifiHelperService;
    private final AttendanceExplanationHelper attendanceExplanationHelper;
    private final sunshine_dental_care.dto.hrDTO.mapper.AttendanceMapper attendanceMapper;

    private final NotificationService notificationService;
    private final ShiftService shiftService;

    private final AttendanceCalculationHelper calculationHelper;
    private final AttendanceValidationHelper validationHelper;
    private final AuditLogService auditLogService;
    private final sunshine_dental_care.repositories.hr.LeaveRequestRepo leaveRequestRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
            "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE", "APPROVED_LATE", "APPROVED_PRESENT");

    @Override
    @Transactional
    public AttendanceResponse checkIn(AttendanceCheckInRequest request) {
        LocalDate today = LocalDate.now();
        validationHelper.validateCheckInAllowed(today);
        attendanceStatusCalculator.validateUserRoleForAttendance(request.getUserId());
        Integer clinicId = clinicResolutionService.resolveClinicId(request.getUserId(), request.getClinicId());

        // Không cho phép chấm công vào clinic đang inactive
        Clinic clinicEntity = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new AttendanceValidationException("Clinic not found: " + clinicId));
        if (!Boolean.TRUE.equals(clinicEntity.getIsActive())) {
            throw new AttendanceValidationException(
                    String.format("Clinic %d is inactive. Please choose another clinic.", clinicId));
        }

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(request.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream().anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(WorkHoursConstants.VN_TIMEZONE);
        Instant checkInTime = Instant.now();
        LocalTime checkInLocalTime = checkInTime.atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime();

        String shiftType;
        Attendance attendance;
        Integer resolvedClinicId = clinicId; // Sẽ được cập nhật cho doctor, giữ nguyên cho employee

        // Xử lý logic chấm công cho bác sĩ và nhân viên
        if (isDoctor) {
            shiftType = shiftService.determineShiftForDoctor(currentTime);
            shiftService.validateCheckInTime(shiftType, currentTime);
            
            // Tìm schedule của ca này để lấy đúng clinicId
            // Ưu tiên: 1. Tìm schedule theo shiftType, 2. Fallback về clinicId từ request/resolution
            List<DoctorSchedule> schedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(request.getUserId(), today);
            if (schedules != null && !schedules.isEmpty()) {
                Optional<DoctorSchedule> matchingSchedule = schedules.stream()
                        .filter(s -> {
                            LocalTime startTime = s.getStartTime();
                            return WorkHoursConstants.matchesShiftType(startTime, shiftType);
                        })
                        .findFirst();
                
                if (matchingSchedule.isPresent()) {
                    DoctorSchedule schedule = matchingSchedule.get();
                    if (schedule.getClinic() != null && schedule.getClinic().getId() != null) {
                        if (!Boolean.TRUE.equals(schedule.getClinic().getIsActive())) {
                            throw new AttendanceValidationException(
                                    String.format("Clinic %d is inactive. Please choose another clinic.",
                                            schedule.getClinic().getId()));
                        }
                        resolvedClinicId = schedule.getClinic().getId();
                    }
                }
            }
            
            final Integer finalClinicId = resolvedClinicId; // Final variable for lambda
            validationHelper.validateUniqueCheckInForDoctor(request.getUserId(), finalClinicId, today, shiftType);

            String finalShiftType = shiftType;
            // Tìm attendance với clinicId đúng từ schedule
            attendance = attendanceRepo.findByUserIdAndClinicIdAndWorkDateAndShiftType(
                    request.getUserId(), finalClinicId, today, shiftType)
                    .orElseGet(() -> {
                        // Nếu không tìm thấy với clinicId, tìm theo userId, workDate, shiftType
                        // (để tránh unique constraint violation - unique constraint là (userId, workDate, shiftType))
                        return attendanceRepo.findByUserIdAndWorkDateAndShiftType(
                                request.getUserId(), today, shiftType)
                                .orElseGet(() -> {
                                    // Tạo mới nếu không tìm thấy
                                    Attendance att = new Attendance();
                                    att.setUserId(request.getUserId());
                                    att.setClinicId(finalClinicId);
                                    att.setWorkDate(today);
                                    att.setShiftType(finalShiftType);
                                    return att;
                                });
                    });
            
            // Cập nhật clinicId nếu khác (trường hợp record đã tồn tại với clinicId khác)
            if (!attendance.getClinicId().equals(finalClinicId)) {
                attendance.setClinicId(finalClinicId);
            }
            // Đảm bảo shiftType luôn được set (không được null để unique constraint hoạt động)
            if (attendance.getShiftType() == null) {
                attendance.setShiftType(finalShiftType);
                log.warn("Fixed null shiftType for attendance record userId={}, clinicId={}, workDate={}, setting to {}",
                        request.getUserId(), finalClinicId, today, finalShiftType);
            }
        } else {
            shiftType = "FULL_DAY";
            validationHelper.validateUniqueCheckInForEmployee(request.getUserId(), clinicId, today);

            final Integer finalClinicIdForEmployee = clinicId; // Final variable for lambda
            attendance = attendanceRepo.findByUserIdAndWorkDate(request.getUserId(), today)
                    .orElseGet(() -> {
                        Attendance att = new Attendance();
                        att.setUserId(request.getUserId());
                        att.setClinicId(finalClinicIdForEmployee);
                        att.setWorkDate(today);
                        att.setShiftType("FULL_DAY");
                        return att;
                    });

            if (!attendance.getClinicId().equals(finalClinicIdForEmployee)) {
                attendance.setClinicId(finalClinicIdForEmployee);
            }
            // Đảm bảo shiftType luôn được set (không được null để unique constraint hoạt động)
            if (attendance.getShiftType() == null || !"FULL_DAY".equals(attendance.getShiftType())) {
                attendance.setShiftType("FULL_DAY");
                if (attendance.getShiftType() == null) {
                    log.warn("Fixed null shiftType for employee attendance record userId={}, clinicId={}, workDate={}, setting to FULL_DAY",
                            request.getUserId(), finalClinicIdForEmployee, today);
                }
            }
        }
        
        // Xác định clinicId cuối cùng để dùng cho các thao tác tiếp theo
        final Integer finalClinicIdForVerification = resolvedClinicId;
        
        // Xác thực wifi và khuôn mặt khi checkin
        WiFiHelperService.WiFiInfo wifiInfo = wifiHelperService.resolveWiFiInfo(request.getSsid(), request.getBssid());
        VerificationResult verification = attendanceVerificationService.verify(
                request.getUserId(), finalClinicIdForVerification, request.getFaceEmbedding(), wifiInfo.getSsid(), wifiInfo.getBssid());

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        attendance.setCheckInTime(checkInTime);
        attendance.setCheckInMethod("FACE_WIFI");
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(request.getNote());
        }
        attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));

        // Tính số phút đi trễ
        LocalTime expectedStartTime = WorkHoursConstants.EMPLOYEE_START_TIME;
        if (isDoctor && shiftType != null) {
            Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    request.getUserId(), finalClinicIdForVerification, today, shiftType);
            if (matchedSchedule.isPresent()) {
                DoctorSchedule schedule = matchedSchedule.get();
                shiftService.activateSchedule(schedule);
                shiftService.activateAfternoonSchedulesIfMorningCheckIn(
                        request.getUserId(), today, shiftType, schedule.getId());
                expectedStartTime = shiftService.getExpectedStartTime(shiftType, schedule);
            } else {
                expectedStartTime = shiftService.getExpectedStartTime(shiftType, null);
            }
        }

        attendance.setLateMinutes(calculationHelper.calculateLateMinutes(checkInLocalTime, expectedStartTime));
        
        // Kiểm tra và đánh ABSENT cho các ca sáng chưa check-in nếu nhân viên check-in muộn hơn 2 giờ
        if (isDoctor && shiftType != null && WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
            // Nếu check-in ca chiều, kiểm tra xem có schedule ca sáng nào chưa check-in không
            checkAndMarkAbsentForMorningShift(request.getUserId(), finalClinicIdForVerification, today, checkInLocalTime);
        }
        
        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        attendance.setVerificationStatus(faceVerified && wifiValid ? "VERIFIED" : "FAILED");

        // Xác định trạng thái điểm danh ban đầu của lần check-in
        String currentStatus = attendance.getAttendanceStatus();
        String currentNote = attendance.getNote();
        boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");
        boolean isApprovedStatus = currentStatus != null && ("APPROVED_LATE".equals(currentStatus) ||
                "APPROVED_ABSENCE".equals(currentStatus) ||
                "APPROVED_PRESENT".equals(currentStatus) ||
                hasApprovedExplanation);

        // Nếu status là APPROVED_ABSENCE nhưng đang check-in, 
        // thì cần cập nhật lại status dựa trên thời gian check-in thực tế
        // vì APPROVED_ABSENCE chỉ hợp lệ khi không có check-in
        // (checkInTime đã được set ở dòng 168, nên luôn != null tại đây)
        boolean shouldUpdateFromApprovedAbsence = "APPROVED_ABSENCE".equals(currentStatus);

        if (currentStatus == null || !isApprovedStatus || shouldUpdateFromApprovedAbsence) {
            String attendanceStatus;
            if (isDoctor && shiftType != null) {
                // Sử dụng expectedStartTime đã tính từ schedule để xác định status chính xác
                attendanceStatus = determineAttendanceStatusForDoctor(
                        request.getUserId(), finalClinicIdForVerification, today, checkInTime, shiftType, expectedStartTime);
            } else {
                attendanceStatus = attendanceStatusCalculator.determineAttendanceStatus(
                        request.getUserId(), finalClinicIdForVerification, today, checkInTime);
            }
            if (shouldUpdateFromApprovedAbsence) {
                log.info("Updating status from APPROVED_ABSENCE to {} for user {} on {} (has check-in time)", 
                        attendanceStatus, request.getUserId(), today);
            }
            attendance.setAttendanceStatus(attendanceStatus);
            
            // Gửi notification nếu bị đánh dấu ABSENT
            if ("ABSENT".equals(attendanceStatus)) {
                // Lưu ý: attendance chưa được save, sẽ gửi notification sau khi save
            }
        }
        // Đảm bảo shiftType luôn được set trước khi save (không được null để unique constraint hoạt động)
        if (attendance.getShiftType() == null) {
            if (isDoctor && shiftType != null) {
                attendance.setShiftType(shiftType);
            } else {
                attendance.setShiftType("FULL_DAY");
            }
            log.warn("Fixed null shiftType for attendance record id={}, userId={}, clinicId={}, workDate={}, setting to {}",
                    attendance.getId(), request.getUserId(), finalClinicIdForVerification, today, attendance.getShiftType());
        }
        attendance = attendanceRepo.save(attendance);

        User user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        sendCheckInNotification(request.getUserId(), clinicEntity.getClinicName(), checkInTime, attendance.getId());
        
        // Gửi notification nếu bị đánh dấu ABSENT
        if ("ABSENT".equals(attendance.getAttendanceStatus())) {
            sendAttendanceAbsentNotification(attendance);
        }

        // Gửi notification DOCTOR_LATE_CHECKIN nếu bác sĩ check-in trễ (> 15 phút)
        if (isDoctor && attendance.getLateMinutes() != null && attendance.getLateMinutes() > 15) {
            sendDoctorLateCheckInNotification(attendance, clinicEntity.getClinicName());
        }

        LocalTime expectedEndTime = WorkHoursConstants.EMPLOYEE_END_TIME;
        if (isDoctor && shiftType != null) {
            Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    request.getUserId(), finalClinicIdForVerification, today, shiftType);
            expectedEndTime = shiftService.getExpectedEndTime(shiftType, matchedSchedule.orElse(null));
        }

        User actor = resolveCurrentUserEntity();
        if (actor != null) {
            auditLogService.logAction(actor, "CHECK_IN", "ATTENDANCE", attendance.getId(), null,
                    "Check-in for user " + request.getUserId() + " at clinic " + clinicId);
        }

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinicEntity, faceProfile, faceResult, wifiResult,
                expectedStartTime, expectedEndTime);
    }

    @Override
    @Transactional
    public AttendanceResponse checkOut(AttendanceCheckOutRequest request, Integer requesterId) {
        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        validationHelper.validateCheckOutAllowed(attendance);
        attendanceStatusCalculator.validateUserRoleForAttendance(attendance.getUserId());
        
        // Kiểm tra quyền được phép khi checkout
        if (requesterId != null && !requesterId.equals(attendance.getUserId())) {
            List<UserRole> requesterRoles = userRoleRepo.findActiveByUserId(requesterId);
            boolean isAdminOrHR = requesterRoles != null && requesterRoles.stream()
                    .anyMatch(ur -> {
                        String roleName = ur.getRole().getRoleName().toUpperCase();
                        return roleName.contains("ADMIN") || roleName.contains("HR");
                    });
            if (!isAdminOrHR) {
                throw new AttendanceValidationException("Only the attendance owner or admin/HR can perform check-out");
            }
        }

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(attendance.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(WorkHoursConstants.VN_TIMEZONE);
        String shiftType = attendance.getShiftType();

        // Kiểm tra thời gian hợp lệ khi checkout
        if (isDoctor && shiftType != null) {
            shiftService.validateCheckOutTime(shiftType, currentTime);
        } else {
            validationHelper.validateCheckOutTimeForEmployee(currentTime);
        }

        // Xác thực wifi và khuôn mặt khi check-out
        WiFiHelperService.WiFiInfo wifiInfo = wifiHelperService.resolveWiFiInfo(request.getSsid(), request.getBssid());
        VerificationResult verification = attendanceVerificationService.verify(
                attendance.getUserId(), attendance.getClinicId(), request.getFaceEmbedding(), wifiInfo.getSsid(), wifiInfo.getBssid());

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        Instant checkOutTime = Instant.now();
        attendance.setCheckOutTime(checkOutTime);

        // Tính số giờ làm thực tế, trễ, về sớm, nghỉ trưa
        LocalTime checkInLocalTime = attendance.getCheckInTime().atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime();
        LocalTime checkOutLocalTime = checkOutTime.atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime();

        LocalTime expectedStartTime = WorkHoursConstants.EMPLOYEE_START_TIME;
        LocalTime expectedEndTime = WorkHoursConstants.EMPLOYEE_END_TIME;
        BigDecimal expectedWorkHours = BigDecimal.valueOf(WorkHoursConstants.EMPLOYEE_EXPECTED_HOURS);

        if (isDoctor && shiftType != null) {
            Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate(), shiftType);
            DoctorSchedule schedule = matchedSchedule.orElse(null);
            expectedStartTime = shiftService.getExpectedStartTime(shiftType, schedule);
            expectedEndTime = shiftService.getExpectedEndTime(shiftType, schedule);
            expectedWorkHours = calculationHelper.calculateExpectedWorkHours(expectedStartTime, expectedEndTime);
        }
        attendance.setExpectedWorkHours(expectedWorkHours);

        // Tính số phút trễ, về sớm
        String currentStatus = attendance.getAttendanceStatus();
        boolean isApprovedLate = "APPROVED_LATE".equals(currentStatus);
        boolean hasApprovedExplanation = attendance.getNote() != null && attendance.getNote().contains("[APPROVED]");

        if (isApprovedLate || hasApprovedExplanation) {
            attendance.setLateMinutes(0);
        } else if (attendance.getLateMinutes() == null) {
            attendance.setLateMinutes(calculationHelper.calculateLateMinutes(checkInLocalTime, expectedStartTime));
        }
        attendance.setEarlyMinutes(calculationHelper.calculateEarlyMinutes(checkOutLocalTime, expectedEndTime));

        // Tính giờ nghỉ trưa nếu là nhân viên
        int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(
                checkInLocalTime, checkOutLocalTime, isDoctor);
        attendance.setLunchBreakMinutes(lunchBreakMinutes);

        // Không tính giờ làm nếu status là ABSENT hoặc APPROVED_ABSENCE (vắng mặt/nghỉ phép được duyệt)
        // Ngay cả khi có check-in/check-out, nếu đã bị đánh dấu ABSENT/APPROVED_ABSENCE thì không tính giờ làm
        if ("ABSENT".equals(currentStatus) || "APPROVED_ABSENCE".equals(currentStatus)) {
            attendance.setActualWorkHours(BigDecimal.ZERO);
            log.info("User {} has {} status on {} - setting actualWorkHours to 0 despite having check-in/check-out", 
                    attendance.getUserId(), currentStatus, attendance.getWorkDate());
        } else {
        attendance.setActualWorkHours(calculationHelper.calculateActualWorkHours(
                attendance.getCheckInTime(), checkOutTime, isDoctor));
        }

        // Đảm bảo shiftType luôn được set trước khi save (không được null để unique constraint hoạt động)
        if (attendance.getShiftType() == null) {
            if (isDoctor && shiftType != null) {
                attendance.setShiftType(shiftType);
            } else {
                attendance.setShiftType("FULL_DAY");
            }
            log.warn("Fixed null shiftType for attendance record id={} during check-out, userId={}, clinicId={}, workDate={}, setting to {}",
                    attendance.getId(), attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate(), attendance.getShiftType());
        }
        
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote((attendance.getNote() != null ? attendance.getNote() + " | " : "") + request.getNote());
        }
        if (attendance.getFaceMatchScore() == null
                || faceResult.getSimilarityScore() > attendance.getFaceMatchScore().doubleValue()) {
            attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));
        }
        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        attendance.setVerificationStatus(faceVerified && wifiValid ? "VERIFIED" : "FAILED");

        attendance = attendanceRepo.save(attendance);

        // Cảnh báo/phòng trường hợp thiếu checkin (chưa xử lý)
        if (attendance.getCheckInTime() == null) {
            
        }

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        sendCheckOutNotification(attendance.getUserId(), clinic.getClinicName(), attendance.getActualWorkHours(), attendance.getId());

        User actor = resolveCurrentUserEntity();
        if (actor != null) {
            auditLogService.logAction(actor, "CHECK_OUT", "ATTENDANCE", attendance.getId(), null,
                    "Check-out for user " + attendance.getUserId() + " at clinic " + attendance.getClinicId());
        }

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult,
                expectedStartTime, expectedEndTime);
    }

    // Kiểm tra và đánh ABSENT cho các ca sáng chưa check-in nếu nhân viên check-in muộn hơn 2 giờ
    private void checkAndMarkAbsentForMorningShift(Integer userId, Integer clinicId, LocalDate workDate, LocalTime checkInTime) {
        try {
            // Lấy tất cả schedule của bác sĩ trong ngày
            List<DoctorSchedule> allSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDateAllStatus(
                    userId, clinicId, workDate);
            
            // Tìm các schedule ca sáng (startTime < 11:00) chưa check-in
            for (DoctorSchedule schedule : allSchedules) {
                if (schedule == null || schedule.getStartTime() == null) {
                    continue;
                }
                
                LocalTime scheduleStartTime = schedule.getStartTime();
                // Chỉ xử lý ca sáng (trước 11:00)
                if (!scheduleStartTime.isBefore(WorkHoursConstants.LUNCH_BREAK_START)) {
                    continue;
                }
                
                // Xác định shiftType của schedule này
                String morningShiftType = WorkHoursConstants.determineShiftType(scheduleStartTime);
                
                // Kiểm tra xem đã có attendance record cho ca sáng này chưa
                Optional<Attendance> existingAttendance = attendanceRepo.findByUserIdAndClinicIdAndWorkDateAndShiftType(
                        userId, clinicId, workDate, morningShiftType);
                
                // Nếu chưa có check-in cho ca sáng này
                if (existingAttendance.isEmpty() || existingAttendance.get().getCheckInTime() == null) {
                    // Tính số phút đi trễ so với giờ bắt đầu ca sáng
                    long minutesLate = 0;
                    if (checkInTime.isAfter(scheduleStartTime)) {
                        minutesLate = java.time.Duration.between(scheduleStartTime, checkInTime).toMinutes();
                    }
                    
                    // Nếu đi trễ hơn 2 giờ (120 phút) so với giờ bắt đầu ca sáng, đánh ABSENT
                    if (minutesLate >= 120) {
                        Attendance absentAttendance = existingAttendance.orElseGet(() -> {
                            Attendance att = new Attendance();
                            att.setUserId(userId);
                            att.setClinicId(clinicId);
                            att.setWorkDate(workDate);
                            att.setShiftType(morningShiftType);
                            return att;
                        });
                        
                        // Chỉ set ABSENT nếu chưa có check-in time
                        if (absentAttendance.getCheckInTime() == null) {
                            boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);
                            String newStatus = hasApprovedLeave ? "APPROVED_ABSENCE" : "ABSENT";
                            absentAttendance.setAttendanceStatus(newStatus);
                            Attendance saved = attendanceRepo.save(absentAttendance);
                            
                            // Gửi notification nếu bị đánh dấu ABSENT
                            if ("ABSENT".equals(newStatus)) {
                                sendAttendanceAbsentNotification(saved);
                            }
                            
                            log.info("Marked doctor {} as {} for MORNING shift at clinic {} on {} (checked in {} minutes late for afternoon shift)", 
                                    userId, newStatus, clinicId, workDate, minutesLate);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking and marking absent for morning shift for user {} at clinic {} on {}: {}", 
                    userId, clinicId, workDate, e.getMessage(), e);
            // Không ném exception để tránh rollback transaction
        }
    }

    // Xác định trạng thái điểm danh cho bác sĩ
    private String determineAttendanceStatusForDoctor(Integer userId, Integer clinicId, LocalDate workDate,
            Instant checkInTime, String shiftType, LocalTime expectedStartTime) {
        LocalTime checkInLocalTime = checkInTime.atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime();
        boolean hasApprovedLeave = leaveRequestRepo.hasApprovedLeaveOnDate(userId, workDate);

        // Tính số phút đi muộn
        long minutesLate = 0;
        if (checkInLocalTime.isAfter(expectedStartTime)) {
            minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
        }

        // Nếu check-in sau 2 giờ (120 phút) từ giờ bắt đầu ca → ABSENT (vắng)
        if (minutesLate >= 120) {
            if (hasApprovedLeave) {
                log.info("Doctor {} checked in APPROVED_ABSENCE: {} minutes late (>= 2 hours) (expected: {}, actual: {}) for shift {} - has leave but absent", 
                        userId, minutesLate, expectedStartTime, checkInLocalTime, shiftType);
                return "APPROVED_ABSENCE";
            } else {
                log.info("Doctor {} checked in ABSENT: {} minutes late (>= 2 hours) (expected: {}, actual: {}) for shift {}", 
                        userId, minutesLate, expectedStartTime, checkInLocalTime, shiftType);
                return "ABSENT";
            }
        }

        // Nếu có đơn nghỉ phép đã duyệt nhưng vẫn chấm công (trong 2 giờ đầu)
        if (hasApprovedLeave) {
            if (checkInLocalTime.isBefore(expectedStartTime) || checkInLocalTime.equals(expectedStartTime)) {
                // Có đơn nghỉ nhưng vẫn đi làm đúng giờ → APPROVED_PRESENT
                log.info("Doctor {} checked in APPROVED_PRESENT with approved leave: {} (expected: {}) for shift {} - has leave but still working on time", 
                        userId, checkInLocalTime, expectedStartTime, shiftType);
                return "APPROVED_PRESENT";
            } else {
                // Có đơn nghỉ và đi muộn (nhưng < 2 giờ) → APPROVED_LATE
                log.info("Doctor {} checked in APPROVED_LATE with approved leave: {} minutes late (expected: {}, actual: {}) for shift {}", 
                        userId, minutesLate, expectedStartTime, checkInLocalTime, shiftType);
                return "APPROVED_LATE";
            }
        } else {
            // Không có đơn nghỉ phép
            if (checkInLocalTime.isBefore(expectedStartTime) || checkInLocalTime.equals(expectedStartTime)) {
                log.info("Doctor {} checked in ON_TIME: {} (expected: {}) for shift {}", 
                        userId, checkInLocalTime, expectedStartTime, shiftType);
                return "ON_TIME";
            } else {
                // Đi muộn nhưng < 2 giờ → LATE
                log.info("Doctor {} checked in LATE: {} minutes late (expected: {}, actual: {}) for shift {}", 
                        userId, minutesLate, expectedStartTime, checkInLocalTime, shiftType);
                return "LATE";
            }
        }
    }

    // Gửi notification khi checkin cho user
    private void sendCheckInNotification(Integer userId, String clinicName, Instant checkInTime, Integer attendanceId) {
        try {
            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(userId)
                    .type("ATTENDANCE_CHECKIN")
                    .priority("LOW")
                    .title("Check-in Successful")
                    .message(String.format("You have successfully checked in at %s. Time: %s",
                            clinicName,
                            checkInTime.atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime().toString().substring(0, 5)))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendanceId)
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send check-in notification", e);
        }
    }

    // Gửi notification khi checkout cho user
    private void sendCheckOutNotification(Integer userId, String clinicName, BigDecimal workHours, Integer attendanceId) {
        try {
            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(userId)
                    .type("ATTENDANCE_CHECKOUT")
                    .priority("LOW")
                    .title("Check-out Successful")
                    .message(String.format("You have successfully checked out at %s. Work hours: %.2f",
                            clinicName,
                            workHours != null ? workHours : 0.0))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendanceId)
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send check-out notification", e);
        }
    }

    /**
     * Gửi notification DOCTOR_LATE_CHECKIN cho Reception, HR, Admin khi bác sĩ check-in trễ (> 15 phút)
     */
    private void sendDoctorLateCheckInNotification(sunshine_dental_care.entities.Attendance attendance, String clinicName) {
        try {
            Integer doctorId = attendance.getUserId();
            Integer clinicId = attendance.getClinicId();
            Integer lateMinutes = attendance.getLateMinutes();
            
            if (lateMinutes == null || lateMinutes <= 15) {
                return; // Chỉ gửi nếu trễ > 15 phút
            }

            User doctor = userRepo.findById(doctorId).orElse(null);
            String doctorName = doctor != null ? doctor.getFullName() : "Bác sĩ";
            
            // Format thời gian
            java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
            java.time.LocalTime checkInTime = attendance.getCheckInTime().atZone(zoneId).toLocalTime();
            String timeStr = checkInTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

            String message = String.format(
                    "Bác sĩ %s đã check-in trễ %d phút tại %s vào lúc %s ngày %s.",
                    doctorName, lateMinutes, clinicName, timeStr, attendance.getWorkDate());

            // Lấy danh sách Reception users của clinic này
            List<Integer> receptionUserIds = getReceptionUserIdsByClinic(clinicId);
            
            // Lấy danh sách HR và Admin users
            List<Integer> hrUserIds = userRoleRepo.findUserIdsByRoleName("HR");
            List<Integer> adminUserIds = userRoleRepo.findUserIdsByRoleName("ADMIN");
            
            // Gộp tất cả user IDs cần thông báo
            java.util.Set<Integer> allUserIds = new java.util.HashSet<>();
            allUserIds.addAll(receptionUserIds);
            allUserIds.addAll(hrUserIds);
            allUserIds.addAll(adminUserIds);

            int successCount = 0;
            for (Integer userId : allUserIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(userId)
                            .type("DOCTOR_LATE_CHECKIN")
                            .priority("MEDIUM")
                            .title("Bác sĩ check-in trễ")
                            .message(message)
                            .actionUrl("/hr/attendance")
                            .relatedEntityType("DOCTOR_SCHEDULE")
                            .relatedEntityId(attendance.getId())
                            .build();

                    notificationService.sendNotification(notiRequest);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to send DOCTOR_LATE_CHECKIN notification to user {}: {}", userId, e.getMessage());
                }
            }

            log.info("Sent {} DOCTOR_LATE_CHECKIN notifications for doctor {} ({} minutes late)", 
                    successCount, doctorName, lateMinutes);
        } catch (Exception e) {
            log.error("Failed to send DOCTOR_LATE_CHECKIN notification for attendance {}: {}", 
                    attendance.getId(), e.getMessage(), e);
        }
    }

    /**
     * Gửi notification ATTENDANCE_ABSENT cho employee khi bị đánh dấu vắng mặt
     */
    private void sendAttendanceAbsentNotification(Attendance attendance) {
        try {
            Integer userId = attendance.getUserId();
            Integer clinicId = attendance.getClinicId();
            
            User user = userRepo.findById(userId).orElse(null);
            Clinic clinic = clinicRepo.findById(clinicId).orElse(null);
            String clinicName = clinic != null ? clinic.getClinicName() : "Phòng khám";
            
            String shiftType = attendance.getShiftType();
            String shiftInfo = "";
            if (shiftType != null && !"FULL_DAY".equals(shiftType)) {
                shiftInfo = " ca " + (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) ? "sáng" : "chiều");
            }

            String message = String.format(
                    "Bạn đã bị đánh dấu vắng mặt%s tại %s ngày %s. Vui lòng liên hệ HR nếu có lý do chính đáng.",
                    shiftInfo, clinicName, attendance.getWorkDate());

            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(userId)
                    .type("ATTENDANCE_ABSENT")
                    .priority("HIGH")
                    .title("Vắng mặt")
                    .message(message)
                    .actionUrl("/my-attendance")
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendance.getId())
                    .build();

            notificationService.sendNotification(notiRequest);
            log.info("Sent ATTENDANCE_ABSENT notification to user {} for attendance {}", userId, attendance.getId());
        } catch (Exception e) {
            log.error("Failed to send ATTENDANCE_ABSENT notification for attendance {}: {}", 
                    attendance.getId(), e.getMessage(), e);
        }
    }

    /**
     * Lấy danh sách reception user IDs của một cơ sở cụ thể
     * Note: Hiện tại chỉ trả về tất cả reception users, không filter theo clinic
     * TODO: Có thể inject UserClinicAssignmentRepo để filter chính xác theo clinic
     */
    private List<Integer> getReceptionUserIdsByClinic(Integer clinicId) {
        try {
            // Lấy tất cả reception user IDs trong hệ thống
            List<Integer> allReceptionUserIds = userRoleRepo.findUserIdsByRoleName("RECEPTION");
            return allReceptionUserIds != null ? allReceptionUserIds : List.of();
        } catch (Exception e) {
            log.error("Failed to get reception user IDs for clinic {}: {}", clinicId, e.getMessage());
            return List.of();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(Integer userId) {
        LocalDate today = LocalDate.now();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream().anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        if (isDoctor) {
            List<Attendance> attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
            if (attendances.isEmpty())
                return null;

            Attendance att = attendances.stream()
                    .min((a1, a2) -> {
                        // Ưu tiên lấy ca có thời gian checkin sớm nhất cho bác sĩ
                        if (a1.getCheckInTime() != null && a2.getCheckInTime() != null) {
                            return a1.getCheckInTime().compareTo(a2.getCheckInTime());
                        }
                        return 0;
                    })
                    .orElse(attendances.get(0));
            return mapToResponseWithLookups(att);
        } else {
            return attendanceRepo.findByUserIdAndWorkDate(userId, today)
                    .map(this::mapToResponseWithLookups)
                    .orElse(null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getTodayAttendanceList(Integer userId) {
        LocalDate today = LocalDate.now();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream().anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));
        
        List<Attendance> attendances;
        if (isDoctor) {
            attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
        } else {
            attendances = attendanceRepo.findByUserIdAndWorkDate(userId, today)
                    .map(java.util.Collections::singletonList)
                    .orElse(java.util.Collections.emptyList());
        }
        
        if (attendances.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        
        // Tối ưu: Fetch user, clinic, faceProfile một lần thay vì từng record
        User user = userRepo.findById(userId).orElse(null);
        java.util.Set<Integer> clinicIds = attendances.stream()
                .map(Attendance::getClinicId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Integer, Clinic> clinicMap = clinicRepo.findAllById(clinicIds).stream()
                .collect(java.util.stream.Collectors.toMap(Clinic::getId, c -> c));
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(userId).orElse(null);
        
        // Map với lookup từ Map
        return attendances.stream().map(att -> {
            Clinic clinic = clinicMap.get(att.getClinicId());
            LocalTime[] times = resolveScheduledTimes(att);
            return attendanceMapper.mapToAttendanceResponse(att, user, clinic, faceProfile, null, null, times[0], times[1]);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttendanceResponse> getAttendanceHistory(
            Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Attendance> attendances;
        if (userId != null) {
            if (clinicId != null) {
                attendances = attendanceRepo.findByUserIdAndClinicIdAndWorkDateBetweenOrderByWorkDateDesc(
                        userId, clinicId, startDate, endDate, pageable);
            } else {
                attendances = attendanceRepo.findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
                        userId, startDate, endDate, pageable);
            }
        } else if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDateBetween(
                    clinicId, startDate, endDate, pageable);
        } else {
            attendances = attendanceRepo.findAll(pageable);
        }
        
        // Tối ưu: Fetch tất cả users, clinics, faceProfiles trước khi map để tránh N+1 queries
        List<Attendance> attendanceList = attendances.getContent();
        if (attendanceList.isEmpty()) {
            return attendances.map(this::mapToResponseWithLookups);
        }
        
        // Collect unique user IDs và clinic IDs
        java.util.Set<Integer> userIds = attendanceList.stream()
                .map(Attendance::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Integer> clinicIds = attendanceList.stream()
                .map(Attendance::getClinicId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        
        // Fetch tất cả users, clinics, faceProfiles trong một lần
        java.util.Map<Integer, User> userMap = userRepo.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        java.util.Map<Integer, Clinic> clinicMap = clinicRepo.findAllById(clinicIds).stream()
                .collect(java.util.stream.Collectors.toMap(Clinic::getId, c -> c));
        java.util.Map<Integer, EmployeeFaceProfile> faceProfileMap = faceProfileRepo.findByUserIdIn(new java.util.ArrayList<>(userIds)).stream()
                .collect(java.util.stream.Collectors.toMap(EmployeeFaceProfile::getUserId, fp -> fp));
        
        // Map với lookup từ Map thay vì query từng record
        return attendances.map(att -> {
            User user = userMap.get(att.getUserId());
            Clinic clinic = clinicMap.get(att.getClinicId());
            EmployeeFaceProfile faceProfile = faceProfileMap.get(att.getUserId());
            LocalTime[] times = resolveScheduledTimes(att);
            return attendanceMapper.mapToAttendanceResponse(att, user, clinic, faceProfile, null, null, times[0], times[1]);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAttendanceStatistics(
            Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate) {
        // Tổng hợp thống kê xuất ra dashboard điểm danh
        List<Attendance> attendances;
        if (userId != null && clinicId != null) {
            attendances = attendanceRepo.findByUserIdAndClinicIdAndWorkDateBetweenOrderByWorkDateDesc(
                    userId, clinicId, startDate, endDate);
        } else if (userId != null) {
            attendances = attendanceRepo.findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
                    userId, startDate, endDate);
        } else if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDateBetween(
                    clinicId, startDate, endDate, Pageable.unpaged()).getContent();
        } else {
            attendances = attendanceRepo.findByWorkDateBetween(startDate, endDate);
        }

        int totalRecords = attendances.size();
        int presentCount = 0;
        int lateCount = 0;
        int absentCount = 0;
        int leaveCount = 0;
        BigDecimal totalHours = BigDecimal.ZERO;
        @SuppressWarnings("unused")
        int recordsWithHours = 0;

        for (Attendance attendance : attendances) {
            String status = attendance.getAttendanceStatus();
            boolean hasCheckIn = attendance.getCheckInTime() != null;
            boolean hasLateMinutes = attendance.getLateMinutes() != null && attendance.getLateMinutes() > 0;
            
            if (status != null) {
                switch (status) {
                    case "ON_TIME", "APPROVED_PRESENT" -> {
                        presentCount++;
                        // Nếu có lateMinutes > 0 nhưng status là ON_TIME/APPROVED_PRESENT, vẫn tính là late
                        if (hasLateMinutes) {
                            lateCount++;
                        }
                    }
                    case "LATE", "APPROVED_LATE" -> {
                        lateCount++;
                        presentCount++;
                    }
                    case "ABSENT" -> absentCount++;
                    case "APPROVED_ABSENCE" -> leaveCount++;
                    case "APPROVED_EARLY_LEAVE" -> {
                        // Nghỉ sớm nhưng vẫn có mặt
                        presentCount++;
                        if (hasLateMinutes) {
                            lateCount++;
                        }
                    }
                    default -> {
                        // Các trạng thái khác: nếu có check-in thì tính là có mặt
                        if (hasCheckIn && !"ABSENT".equals(status) && !"APPROVED_ABSENCE".equals(status)) {
                            presentCount++;
                            if (hasLateMinutes) {
                                lateCount++;
                            }
                        }
                    }
                }
            } else {
                // Nếu status = null nhưng có check-in, tính là có mặt
                if (hasCheckIn) {
                    presentCount++;
                    if (hasLateMinutes) {
                        lateCount++;
                    }
                }
            }

            // Chỉ cộng giờ làm nếu status không phải ABSENT hoặc APPROVED_ABSENCE
            // ABSENT/APPROVED_ABSENCE nghĩa là vắng mặt/nghỉ phép, không nên tính giờ làm dù có check-in/check-out
            if (attendance.getActualWorkHours() != null && 
                !"ABSENT".equals(status) && !"APPROVED_ABSENCE".equals(status)) {
                totalHours = totalHours.add(attendance.getActualWorkHours());
                recordsWithHours++;
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("presentCount", presentCount);
        stats.put("lateCount", lateCount);
        stats.put("absentCount", absentCount);
        stats.put("leaveCount", leaveCount);
        stats.put("totalHours", totalHours.doubleValue());
        return stats;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getAttendanceById(Integer attendanceId) {
        Attendance attendance = attendanceRepo.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));
        return mapToResponseWithLookups(attendance);
    }

    @Override
    public List<DailySummaryResponse> getDailySummary(LocalDate workDate) {
        return attendanceReportService.getDailySummary(workDate);
    }

    @Override
    public Page<DailyAttendanceListItemResponse> getDailyAttendanceList(
            LocalDate workDate, Integer departmentId, Integer clinicId, int page, int size) {
        return attendanceReportService.getDailyAttendanceList(workDate, departmentId, clinicId, page, size);
    }

    @Override
    public List<MonthlySummaryResponse> getMonthlySummary(Integer year, Integer month) {
        return attendanceReportService.getMonthlySummary(year, month);
    }

    @Override
    public Page<MonthlyAttendanceListItemResponse> getMonthlyAttendanceList(
            Integer year, Integer month, Integer departmentId, Integer clinicId, int page, int size) {
        return attendanceReportService.getMonthlyAttendanceList(year, month, departmentId, clinicId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceForAdmin(LocalDate workDate, Integer clinicId, String status) {
        LocalDate targetDate = workDate != null ? workDate : LocalDate.now();
        String normalizedStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : null;

        List<Attendance> attendances;
        if (clinicId != null && normalizedStatus != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDateAndAttendanceStatus(clinicId, targetDate, normalizedStatus);
        } else if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDate(clinicId, targetDate);
        } else if (normalizedStatus != null) {
            attendances = attendanceRepo.findByWorkDateAndAttendanceStatus(targetDate, normalizedStatus);
        } else {
            attendances = attendanceRepo.findByWorkDate(targetDate);
        }
        return attendances.stream().map(this::mapToResponseWithLookups).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AttendanceResponse updateAttendanceStatus(Integer attendanceId, String newStatus, String adminNote, Integer adminUserId) {
        Attendance attendance = attendanceRepo.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));

        // Kiểm tra trạng thái mới có nằm trong danh sách được phép cập nhật bởi admin hay không
        if (newStatus != null && !ADMIN_ALLOWED_STATUSES.contains(newStatus.toUpperCase())) {
            throw new AttendanceValidationException(
                    String.format("Status '%s' is not allowed. Allowed statuses: %s", 
                            newStatus, ADMIN_ALLOWED_STATUSES));
        }

        String oldStatus = attendance.getAttendanceStatus();
        attendance.setAttendanceStatus(newStatus);
        // Thêm note của admin vào ghi chú
        if (adminNote != null && !adminNote.trim().isEmpty()) {
            attendance.setNote(
                    (attendance.getNote() != null ? attendance.getNote() + " | " : "") + "[Admin: " + adminNote + "]");
        }
        attendance.setUpdatedAt(Instant.now());
        Attendance saved = attendanceRepo.save(attendance);

        // Gửi notification nếu status được đổi sang ABSENT
        if ("ABSENT".equals(newStatus) && !"ABSENT".equals(oldStatus)) {
            sendAttendanceAbsentNotification(saved);
        }

        User actor = resolveUserById(adminUserId);
        if (actor != null) {
            auditLogService.logAction(actor, "APPROVE_ATTENDANCE", "ATTENDANCE", attendanceId, null,
                    "Status -> " + newStatus + (adminNote != null ? " | note: " + adminNote : ""));
        }

        return mapToResponseWithLookups(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> getAttendanceNeedingExplanation(Integer userId) {
        return attendanceExplanationHelper.getAttendanceNeedingExplanation(userId);
    }

    @Override
    @Transactional
    public AttendanceResponse submitExplanation(AttendanceExplanationRequest request, Integer userId) {
        Attendance attendance = attendanceExplanationHelper.submitExplanation(request, userId);
        return mapToResponseWithLookups(attendance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> getPendingExplanations(Integer clinicId) {
        return attendanceExplanationHelper.getPendingExplanations(clinicId);
    }

    @Override
    @Transactional
    public AttendanceResponse processExplanation(AdminExplanationActionRequest request, Integer adminUserId) {
        Attendance attendance = attendanceExplanationHelper.processExplanation(request, adminUserId);

        User actor = resolveUserById(adminUserId);
        if (actor != null) {
            auditLogService.logAction(actor, request.getAction(), "ATTENDANCE", request.getAttendanceId(), null,
                    "Process explanation: " + request.getAction() +
                            (request.getAdminNote() != null ? " | note: " + request.getAdminNote() : ""));
        }

        return mapToResponseWithLookups(attendance);
    }

    // Map sang dữ liệu response kèm lookup user, clinic, profile, thời gian dự kiến ca làm
    private AttendanceResponse mapToResponseWithLookups(Attendance attendance) {
        User user = userRepo.findById(attendance.getUserId()).orElse(null);
        Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(attendance.getUserId()).orElse(null);
        LocalTime[] times = resolveScheduledTimes(attendance);
        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, null, null, times[0], times[1]);
    }

    // Xác định thời gian dự kiến cho ca làm dựa vào shiftType và schedule
    private LocalTime[] resolveScheduledTimes(Attendance attendance) {
        if (attendance == null) {
            return new LocalTime[] { WorkHoursConstants.EMPLOYEE_START_TIME, WorkHoursConstants.EMPLOYEE_END_TIME };
        }
        
        String shiftType = attendance.getShiftType();
        
        // Nếu là bác sĩ có shiftType, lấy giờ từ shiftType hoặc schedule
        if (shiftType != null && 
            (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType) || 
             WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType))) {
            
            // Thử lấy từ schedule nếu có
            Optional<DoctorSchedule> schedule = shiftService.findMatchingSchedule(
                    attendance.getUserId(), 
                    attendance.getClinicId(), 
                    attendance.getWorkDate(), 
                    shiftType);
            
            if (schedule.isPresent()) {
                DoctorSchedule s = schedule.get();
                LocalTime scheduleStart = s.getStartTime();
                LocalTime scheduleEnd = s.getEndTime();
                if (scheduleStart != null && scheduleEnd != null) {
                    return new LocalTime[] { scheduleStart, scheduleEnd };
                }
            }
            
            // Fallback về constants theo shiftType
            if (WorkHoursConstants.SHIFT_TYPE_MORNING.equals(shiftType)) {
                return new LocalTime[] { 
                    WorkHoursConstants.MORNING_SHIFT_START, 
                    WorkHoursConstants.MORNING_SHIFT_END 
                };
            } else if (WorkHoursConstants.SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
                return new LocalTime[] { 
                    WorkHoursConstants.AFTERNOON_SHIFT_START, 
                    WorkHoursConstants.AFTERNOON_SHIFT_END 
                };
            }
        }
        
        // Nhân viên hoặc FULL_DAY: dùng giờ mặc định 08:00-18:00
        return new LocalTime[] { 
            WorkHoursConstants.EMPLOYEE_START_TIME, 
            WorkHoursConstants.EMPLOYEE_END_TIME 
        };
    }

    // Helper: lấy user hiện tại từ SecurityContext
    private User resolveCurrentUserEntity() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CurrentUser currentUser) {
                return userRepo.findById(currentUser.userId()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Cannot resolve current user for audit log: {}", e.getMessage());
        }
        return null;
    }

    // Helper: lấy user theo id (cho các action truyền vào adminUserId)
    private User resolveUserById(Integer userId) {
        if (userId == null) return null;
        try {
            return userRepo.findById(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Cannot resolve user {} for audit log: {}", userId, e.getMessage());
            return null;
        }
    }
}
