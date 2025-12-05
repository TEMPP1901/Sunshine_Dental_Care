package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.services.impl.hr.ClinicResolutionService;
import sunshine_dental_care.services.impl.hr.wifi.WiFiHelperService;
import sunshine_dental_care.services.impl.hr.attend.AttendanceVerificationService.VerificationResult;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService.FaceVerificationResult;
import sunshine_dental_care.services.interfaces.hr.ShiftService;

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

    private static final LocalTime EMPLOYEE_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime EMPLOYEE_END_TIME = LocalTime.of(18, 0);
    private static final BigDecimal EMPLOYEE_EXPECTED_HOURS = BigDecimal.valueOf(8);

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
            "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE", "APPROVED_LATE", "APPROVED_PRESENT");

    @Override
    @Transactional
    public AttendanceResponse checkIn(AttendanceCheckInRequest request) {
        LocalDate today = LocalDate.now();
        validationHelper.validateCheckInAllowed(today);
        attendanceStatusCalculator.validateUserRoleForAttendance(request.getUserId());
        Integer clinicId = clinicResolutionService.resolveClinicId(request.getUserId(), request.getClinicId());

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(request.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream().anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        Instant checkInTime = Instant.now();
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

        String shiftType = null;
        Attendance attendance;

        if (isDoctor) {
            shiftType = shiftService.determineShiftForDoctor(currentTime);
            shiftService.validateCheckInTime(shiftType, currentTime);
            validationHelper.validateUniqueCheckInForDoctor(request.getUserId(), clinicId, today, shiftType);

            String finalShiftType = shiftType;
            attendance = attendanceRepo.findByUserIdAndClinicIdAndWorkDateAndShiftType(
                    request.getUserId(), clinicId, today, shiftType)
                    .orElseGet(() -> {
                        Attendance att = new Attendance();
                        att.setUserId(request.getUserId());
                        att.setClinicId(clinicId);
                        att.setWorkDate(today);
                        att.setShiftType(finalShiftType);
                        return att;
                    });
        } else {
            shiftType = "FULL_DAY";
            validationHelper.validateUniqueCheckInForEmployee(request.getUserId(), clinicId, today);

            attendance = attendanceRepo.findByUserIdAndWorkDate(request.getUserId(), today)
                    .orElseGet(() -> {
                        Attendance att = new Attendance();
                        att.setUserId(request.getUserId());
                        att.setClinicId(clinicId);
                        att.setWorkDate(today);
                        att.setShiftType("FULL_DAY");
                        return att;
                    });

            if (!attendance.getClinicId().equals(clinicId)) {
                attendance.setClinicId(clinicId);
            }
            if (attendance.getShiftType() == null || !"FULL_DAY".equals(attendance.getShiftType())) {
                attendance.setShiftType("FULL_DAY");
            }
        }

        WiFiHelperService.WiFiInfo wifiInfo = wifiHelperService.resolveWiFiInfo(request.getSsid(), request.getBssid());
        VerificationResult verification = attendanceVerificationService.verify(
                request.getUserId(), clinicId, request.getFaceEmbedding(), wifiInfo.getSsid(), wifiInfo.getBssid());

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
        LocalTime expectedStartTime = EMPLOYEE_START_TIME;
        if (isDoctor && shiftType != null) {
            Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    request.getUserId(), clinicId, today, shiftType);
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
        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        attendance.setVerificationStatus(faceVerified && wifiValid ? "VERIFIED" : "FAILED");

        // Xác định trạng thái điểm danh 
        String currentStatus = attendance.getAttendanceStatus();
        String currentNote = attendance.getNote();
        boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");
        boolean isApprovedStatus = currentStatus != null && ("APPROVED_LATE".equals(currentStatus) ||
                "APPROVED_ABSENCE".equals(currentStatus) ||
                "APPROVED_PRESENT".equals(currentStatus) ||
                hasApprovedExplanation);

        if (currentStatus == null || !isApprovedStatus) {
            String attendanceStatus = (isDoctor && shiftType != null)
                    ? determineAttendanceStatusForDoctor(request.getUserId(), clinicId, today, checkInTime, shiftType)
                    : attendanceStatusCalculator.determineAttendanceStatus(request.getUserId(), clinicId, today, checkInTime);
            attendance.setAttendanceStatus(attendanceStatus);
        }
        attendance = attendanceRepo.save(attendance);

        User user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        sendCheckInNotification(request.getUserId(), clinic.getClinicName(), checkInTime, attendance.getId());

        LocalTime expectedEndTime = EMPLOYEE_END_TIME;
        if (isDoctor && shiftType != null) {
            Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    request.getUserId(), clinicId, today, shiftType);
            expectedEndTime = shiftService.getExpectedEndTime(shiftType, matchedSchedule.orElse(null));
        }

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult,
                expectedStartTime, expectedEndTime);
    }

    @Override
    @Transactional
    public AttendanceResponse checkOut(AttendanceCheckOutRequest request) {
        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        validationHelper.validateCheckOutAllowed(attendance);
        attendanceStatusCalculator.validateUserRoleForAttendance(attendance.getUserId());

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(attendance.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        String shiftType = attendance.getShiftType();

        if (isDoctor && shiftType != null) {
            shiftService.validateCheckOutTime(shiftType, currentTime);
        } else {
            validationHelper.validateCheckOutTimeForEmployee(currentTime);
        }

        WiFiHelperService.WiFiInfo wifiInfo = wifiHelperService.resolveWiFiInfo(request.getSsid(), request.getBssid());
        VerificationResult verification = attendanceVerificationService.verify(
                attendance.getUserId(), attendance.getClinicId(), request.getFaceEmbedding(), wifiInfo.getSsid(), wifiInfo.getBssid());

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        Instant checkOutTime = Instant.now();
        attendance.setCheckOutTime(checkOutTime);

        // Tính thời gian làm việc thực tế
        LocalTime checkInLocalTime = attendance.getCheckInTime().atZone(ZoneId.systemDefault()).toLocalTime();
        LocalTime checkOutLocalTime = checkOutTime.atZone(ZoneId.systemDefault()).toLocalTime();

        LocalTime expectedStartTime = EMPLOYEE_START_TIME;
        LocalTime expectedEndTime = EMPLOYEE_END_TIME;
        BigDecimal expectedWorkHours = EMPLOYEE_EXPECTED_HOURS;

        if (isDoctor && shiftType != null) {
            Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(
                    attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate(), shiftType);
            DoctorSchedule schedule = matchedSchedule.orElse(null);
            expectedStartTime = shiftService.getExpectedStartTime(shiftType, schedule);
            expectedEndTime = shiftService.getExpectedEndTime(shiftType, schedule);
            expectedWorkHours = calculationHelper.calculateExpectedWorkHours(expectedStartTime, expectedEndTime);
        }
        attendance.setExpectedWorkHours(expectedWorkHours);

        // Xử lý số phút đi trễ và về sớm
        String currentStatus = attendance.getAttendanceStatus();
        boolean isApprovedLate = "APPROVED_LATE".equals(currentStatus);
        boolean hasApprovedExplanation = attendance.getNote() != null && attendance.getNote().contains("[APPROVED]");

        if (isApprovedLate || hasApprovedExplanation) {
            attendance.setLateMinutes(0);
        } else if (attendance.getLateMinutes() == null) {
            attendance.setLateMinutes(calculationHelper.calculateLateMinutes(checkInLocalTime, expectedStartTime));
        }

        attendance.setEarlyMinutes(calculationHelper.calculateEarlyMinutes(checkOutLocalTime, expectedEndTime));

        // Trừ giờ ăn trưa nếu là nhân viên và chấm công trước 11h, về sau 13h
        long lunchBreakMinutes = (!isDoctor && checkInLocalTime.isBefore(LocalTime.of(11, 0))
                && checkOutLocalTime.isAfter(LocalTime.of(13, 0))) ? 120 : 0;
        attendance.setLunchBreakMinutes((int) lunchBreakMinutes);

        Duration totalDuration = Duration.between(attendance.getCheckInTime(), checkOutTime);
        attendance.setActualWorkHours(calculationHelper.calculateActualWorkHours(totalDuration, lunchBreakMinutes));

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

        // Kiểm tra trường hợp thiếu checkin (đảm bảo an toàn)
        if (attendance.getCheckInTime() == null) {
            // Có thể xử lý checkin bị null ở đây nếu cần
        }

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        sendCheckOutNotification(attendance.getUserId(), clinic.getClinicName(), attendance.getActualWorkHours(), attendance.getId());

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult,
                expectedStartTime, expectedEndTime);
    }

    // Lấy trạng thái điểm danh cho bác sĩ
    private String determineAttendanceStatusForDoctor(Integer userId, Integer clinicId, LocalDate workDate,
            Instant checkInTime, String shiftType) {
        return attendanceStatusCalculator.determineAttendanceStatus(userId, clinicId, workDate, checkInTime);
    }

    // Gửi thông báo checkin
    private void sendCheckInNotification(Integer userId, String clinicName, Instant checkInTime, Integer attendanceId) {
        try {
            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(userId)
                    .type("ATTENDANCE_CHECKIN")
                    .priority("LOW")
                    .title("Check-in Successful")
                    .message(String.format("You have successfully checked in at %s. Time: %s",
                            clinicName,
                            checkInTime.atZone(ZoneId.systemDefault()).toLocalTime().toString().substring(0, 5)))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendanceId)
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send check-in notification", e);
        }
    }

    // Gửi thông báo checkout
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
                        // Sắp xếp lấy ca đầu tiên của bác sĩ ngày hôm nay
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
        if (isDoctor) {
            return attendanceRepo.findAllByUserIdAndWorkDate(userId, today).stream()
                    .map(this::mapToResponseWithLookups)
                    .collect(Collectors.toList());
        } else {
            return attendanceRepo.findByUserIdAndWorkDate(userId, today)
                    .map(this::mapToResponseWithLookups)
                    .stream().collect(Collectors.toList());
        }
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
        return attendances.map(this::mapToResponseWithLookups);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAttendanceStatistics(
            Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate) {
        // Tổng hợp thống kê chấm công cho dashboard
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
        int recordsWithHours = 0;

        for (Attendance attendance : attendances) {
            String status = attendance.getAttendanceStatus();
            if ("ON_TIME".equals(status) || "APPROVED_PRESENT".equals(status))
                presentCount++;
            else if ("LATE".equals(status) || "APPROVED_LATE".equals(status)) {
                lateCount++;
                presentCount++;
            } else if ("ABSENT".equals(status))
                absentCount++;
            else if ("APPROVED_ABSENCE".equals(status))
                leaveCount++;

            if (attendance.getActualWorkHours() != null) {
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

        attendance.setAttendanceStatus(newStatus);
        // Ghi chú của admin vào note
        if (adminNote != null && !adminNote.trim().isEmpty()) {
            attendance.setNote(
                    (attendance.getNote() != null ? attendance.getNote() + " | " : "") + "[Admin: " + adminNote + "]");
        }
        attendance.setUpdatedAt(Instant.now());
        return mapToResponseWithLookups(attendanceRepo.save(attendance));
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
        return mapToResponseWithLookups(attendance);
    }

    // map thông tin lookup (user, clinic, profile) + giờ ca làm cho attendance
    private AttendanceResponse mapToResponseWithLookups(Attendance attendance) {
        User user = userRepo.findById(attendance.getUserId()).orElse(null);
        Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(attendance.getUserId()).orElse(null);
        LocalTime[] times = resolveScheduledTimes(attendance);
        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, null, null, times[0], times[1]);
    }

    // Xác định thời gian ca dự kiến dựa theo ca làm
    private LocalTime[] resolveScheduledTimes(Attendance attendance) {
        LocalTime start = EMPLOYEE_START_TIME;
        LocalTime end = EMPLOYEE_END_TIME;
        return new LocalTime[] { start, end };
    }
}
