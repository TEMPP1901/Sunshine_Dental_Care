package sunshine_dental_care.services.impl.hr;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AlreadyCheckedInException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceNotFoundException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.services.impl.hr.AttendanceVerificationService.VerificationResult;
import sunshine_dental_care.services.impl.notification.NotificationService;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;
import sunshine_dental_care.services.interfaces.hr.ShiftService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService.FaceVerificationResult;

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
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final ClinicResolutionService clinicResolutionService;
    private final WiFiHelperService wifiHelperService;
    private final AttendanceExplanationHelper attendanceExplanationHelper;
    private final sunshine_dental_care.dto.hrDTO.mapper.AttendanceMapper attendanceMapper;

    private final NotificationService notificationService;
    private final ShiftService shiftService;

    private static final LocalTime EMPLOYEE_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime EMPLOYEE_END_TIME = LocalTime.of(18, 0);
    private static final BigDecimal EMPLOYEE_EXPECTED_HOURS = BigDecimal.valueOf(8);
    private static final int MAX_LATE_MINUTES_THRESHOLD = 120;

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
            "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE", "APPROVED_LATE", "APPROVED_PRESENT");

    // Xử lý check-in cho nhân viên hoặc bác sĩ, tính trạng thái, cập nhật lịch bác
    // sĩ
    @Override
    @Transactional
    public AttendanceResponse checkIn(AttendanceCheckInRequest request) {
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new AttendanceValidationException("Attendance is not allowed on Sundays");
        }
        attendanceStatusCalculator.validateUserRoleForAttendance(request.getUserId());

        Integer clinicId = clinicResolutionService.resolveClinicId(request.getUserId(), request.getClinicId());

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(request.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        Instant checkInTime = Instant.now();
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

        String shiftType = null;
        Attendance attendance;

        if (isDoctor) {
            shiftType = shiftService.determineShiftForDoctor(currentTime);
            shiftService.validateCheckInTime(shiftType, currentTime);

            Optional<Attendance> existingShift = attendanceRepo
                    .findByUserIdAndClinicIdAndWorkDateAndShiftType(
                            request.getUserId(), clinicId, today, shiftType);
            if (existingShift.isPresent() && existingShift.get().getCheckInTime() != null) {
                throw new AlreadyCheckedInException(
                        String.format("Doctor %d already checked in for %s shift at clinic %d on %s",
                                request.getUserId(), shiftType, clinicId, today));
            }
            String finalShiftType = shiftType;
            attendance = existingShift.orElseGet(() -> {
                Attendance att = new Attendance();
                att.setUserId(request.getUserId());
                att.setClinicId(clinicId);
                att.setWorkDate(today);
                att.setShiftType(finalShiftType);
                return att;
            });
        } else {
            shiftType = "FULL_DAY";
            Optional<Attendance> existing = attendanceRepo
                    .findByUserIdAndWorkDate(request.getUserId(), today);

            if (existing.isPresent()) {
                Attendance existingAtt = existing.get();
                if (!existingAtt.getClinicId().equals(clinicId)) {
                    existingAtt.setClinicId(clinicId);
                }
                if (existingAtt.getCheckInTime() != null) {
                    throw new AlreadyCheckedInException(
                            String.format("Employee %d already checked in at clinic %d on %s",
                                    request.getUserId(), existingAtt.getClinicId(), today));
                }
                attendance = existingAtt;
            } else {
                Attendance att = new Attendance();
                att.setUserId(request.getUserId());
                att.setClinicId(clinicId);
                att.setWorkDate(today);
                att.setShiftType("FULL_DAY");
                attendance = att;
            }
            if (attendance.getShiftType() == null || !"FULL_DAY".equals(attendance.getShiftType())) {
                attendance.setShiftType("FULL_DAY");
            }
        }

        WiFiHelperService.WiFiInfo wifiInfo = wifiHelperService.resolveWiFiInfo(request.getSsid(), request.getBssid());
        String ssid = wifiInfo.getSsid();
        String bssid = wifiInfo.getBssid();

        VerificationResult verification = attendanceVerificationService.verify(
                request.getUserId(),
                clinicId,
                request.getFaceEmbedding(),
                ssid,
                bssid);

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        attendance.setCheckInTime(checkInTime);
        attendance.setCheckInMethod("FACE_WIFI");
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(request.getNote());
        }
        attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));

        // Calculate Late Minutes
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
        } else {
            expectedStartTime = EMPLOYEE_START_TIME;
        }

        if (checkInLocalTime.isAfter(expectedStartTime)) {
            long actualMinutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
            long minutesLate = actualMinutesLate >= MAX_LATE_MINUTES_THRESHOLD ? MAX_LATE_MINUTES_THRESHOLD
                    : actualMinutesLate;
            attendance.setLateMinutes((int) minutesLate);
        } else {
            attendance.setLateMinutes(0);
        }

        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        attendance.setVerificationStatus(faceVerified && wifiValid ? "VERIFIED" : "FAILED");

        // Determine Attendance Status
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
                    : attendanceStatusCalculator.determineAttendanceStatus(request.getUserId(), clinicId, today,
                            checkInTime);
            attendance.setAttendanceStatus(attendanceStatus);
        }
        attendance = attendanceRepo.save(attendance);

        User user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        // Send Notification
        try {
            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(request.getUserId())
                    .type("ATTENDANCE_CHECKIN")
                    .priority("LOW")
                    .title("Check-in Successful")
                    .message(String.format("You have successfully checked in at %s. Time: %s",
                            clinic.getClinicName(),
                            checkInTime.atZone(ZoneId.systemDefault()).toLocalTime().toString().substring(0, 5)))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendance.getId())
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send check-in notification", e);
        }

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }

    // Xử lý check-out cho nhân viên hoặc bác sĩ, tính giờ công thực tế
    @Override
    @Transactional
    public AttendanceResponse checkOut(AttendanceCheckOutRequest request) {
        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        if (attendance.getCheckInTime() == null) {
            throw new AttendanceValidationException("Cannot check out without check-in");
        }
        if (attendance.getCheckOutTime() != null) {
            throw new AttendanceValidationException("Already checked out");
        }

        attendanceStatusCalculator.validateUserRoleForAttendance(attendance.getUserId());

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(attendance.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        String shiftType = attendance.getShiftType();

        if (isDoctor && shiftType != null) {
            shiftService.validateCheckOutTime(shiftType, currentTime);
        } else {
            if (currentTime.isBefore(LocalTime.of(13, 0))) {
                throw new AttendanceValidationException(
                        String.format("Check-out is allowed only after lunch break (after 13:00). Current time: %s",
                                currentTime));
            }
        }

        WiFiHelperService.WiFiInfo wifiInfo = wifiHelperService.resolveWiFiInfo(request.getSsid(), request.getBssid());
        String ssid = wifiInfo.getSsid();
        String bssid = wifiInfo.getBssid();

        VerificationResult verification = attendanceVerificationService.verify(
                attendance.getUserId(),
                attendance.getClinicId(),
                request.getFaceEmbedding(),
                ssid,
                bssid);

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        Instant checkOutTime = Instant.now();
        attendance.setCheckOutTime(checkOutTime);

        // Calculate Work Hours
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

            long expectedMinutes = java.time.Duration.between(expectedStartTime, expectedEndTime).toMinutes();
            expectedWorkHours = BigDecimal.valueOf(expectedMinutes).divide(BigDecimal.valueOf(60), 2,
                    java.math.RoundingMode.HALF_UP);
        }
        attendance.setExpectedWorkHours(expectedWorkHours);

        long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), checkOutTime).toMinutes();

        // Late Minutes Logic
        String currentStatus = attendance.getAttendanceStatus();
        boolean isApprovedLate = "APPROVED_LATE".equals(currentStatus);
        boolean hasApprovedExplanation = attendance.getNote() != null
                && attendance.getNote().contains("[APPROVED]");

        long minutesLate;
        if (isApprovedLate || hasApprovedExplanation) {
            minutesLate = 0;
            attendance.setLateMinutes(0);
            log.info("Check-out for APPROVED_LATE attendance {}: lateMinutes reset to 0", attendance.getId());
        } else if (attendance.getLateMinutes() != null) {
            minutesLate = attendance.getLateMinutes();
        } else {
            if (checkInLocalTime.isAfter(expectedStartTime)) {
                long actualMinutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                minutesLate = actualMinutesLate >= MAX_LATE_MINUTES_THRESHOLD ? MAX_LATE_MINUTES_THRESHOLD
                        : actualMinutesLate;
                attendance.setLateMinutes((int) minutesLate);
            } else {
                minutesLate = 0;
                attendance.setLateMinutes(0);
            }
        }

        // Early Minutes Logic
        if (checkOutLocalTime.isBefore(expectedEndTime)) {
            long minutesEarly = java.time.Duration.between(checkOutLocalTime, expectedEndTime).toMinutes();
            attendance.setEarlyMinutes((int) minutesEarly);
        } else {
            attendance.setEarlyMinutes(0);
        }

        long lunchBreakMinutes = 0;
        if (!isDoctor && checkInLocalTime.isBefore(LocalTime.of(11, 0))
                && checkOutLocalTime.isAfter(LocalTime.of(13, 0))) {
            lunchBreakMinutes = 120;
            attendance.setLunchBreakMinutes(120);
        } else {
            attendance.setLunchBreakMinutes(0);
        }

        long minutesEarly = attendance.getEarlyMinutes() != null ? attendance.getEarlyMinutes() : 0;
        long adjustedMinutes = totalMinutes - minutesLate - minutesEarly - lunchBreakMinutes;
        if (adjustedMinutes < 0) {
            adjustedMinutes = 0;
        }
        BigDecimal actualHours = BigDecimal.valueOf(adjustedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
        attendance.setActualWorkHours(actualHours);

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

        if (attendance.getCheckInTime() == null) {
            String status = attendance.getAttendanceStatus();
            String note = attendance.getNote();
            boolean approved = note != null && note.contains("[APPROVED]");
            boolean approvedStatus = "APPROVED_ABSENCE".equals(status) ||
                    "APPROVED_PRESENT".equals(status) ||
                    approved;

            if (!approvedStatus) {
                attendance.setAttendanceStatus("ABSENT");
                attendance = attendanceRepo.save(attendance);
            }
        }

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        try {
            NotificationRequest notiRequest = NotificationRequest.builder()
                    .userId(attendance.getUserId())
                    .type("ATTENDANCE_CHECKOUT")
                    .priority("LOW")
                    .title("Check-out Successful")
                    .message(String.format("You have successfully checked out at %s. Work hours: %.2f",
                            clinic.getClinicName(),
                            attendance.getActualWorkHours() != null ? attendance.getActualWorkHours() : 0.0))
                    .relatedEntityType("ATTENDANCE")
                    .relatedEntityId(attendance.getId())
                    .build();
            notificationService.sendNotification(notiRequest);
        } catch (Exception e) {
            log.error("Failed to send check-out notification", e);
        }

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }

    // Lấy bản ghi attendance hôm nay cho user theo role
    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(Integer userId) {
        LocalDate today = LocalDate.now();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null
                && userRoles.stream().anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        if (isDoctor) {
            List<Attendance> attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
            if (attendances.isEmpty()) {
                return null;
            }
            Attendance att = attendances.stream()
                    .min((a1, a2) -> {
                        String s1 = a1.getShiftType() != null ? a1.getShiftType() : "";
                        String s2 = a2.getShiftType() != null ? a2.getShiftType() : "";
                        if ("MORNING".equals(s1) && "AFTERNOON".equals(s2))
                            return -1;
                        if ("AFTERNOON".equals(s1) && "MORNING".equals(s2))
                            return 1;
                        if (a1.getCheckInTime() != null && a2.getCheckInTime() != null) {
                            return a1.getCheckInTime().compareTo(a2.getCheckInTime());
                        }
                        return 0;
                    })
                    .orElse(attendances.get(0));
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
            Clinic clinic = clinicRepo.findById(att.getClinicId())
                    .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

            return attendanceMapper.mapToAttendanceResponse(att, user, clinic, null, null, null);
        } else {
            Optional<Attendance> attendance = attendanceRepo.findByUserIdAndWorkDate(userId, today);
            if (attendance.isEmpty()) {
                return null;
            }
            Attendance att = attendance.get();
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
            Clinic clinic = clinicRepo.findById(att.getClinicId())
                    .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));
            return attendanceMapper.mapToAttendanceResponse(att, user, clinic, null, null, null);
        }
    }

    // Lấy danh sách bản ghi attendance hôm nay cho user theo role
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getTodayAttendanceList(Integer userId) {
        LocalDate today = LocalDate.now();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));
        if (isDoctor) {
            List<Attendance> attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
            if (attendances.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            attendances.sort((a1, a2) -> {
                String s1 = a1.getShiftType() != null ? a1.getShiftType() : "";
                String s2 = a2.getShiftType() != null ? a2.getShiftType() : "";
                if ("MORNING".equals(s1) && "AFTERNOON".equals(s2))
                    return -1;
                if ("AFTERNOON".equals(s1) && "MORNING".equals(s2))
                    return 1;
                if (a1.getCheckInTime() != null && a2.getCheckInTime() != null) {
                    return a1.getCheckInTime().compareTo(a2.getCheckInTime());
                }
                return 0;
            });
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
            return attendances.stream()
                    .map(att -> {
                        Clinic clinic = clinicRepo.findById(att.getClinicId()).orElse(null);
                        return attendanceMapper.mapToAttendanceResponse(att, user, clinic, null, null, null);
                    })
                    .collect(java.util.stream.Collectors.toList());
        } else {
            Optional<Attendance> attendance = attendanceRepo.findByUserIdAndWorkDate(userId, today);
            if (attendance.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            Attendance att = attendance.get();
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
            Clinic clinic = clinicRepo.findById(att.getClinicId())
                    .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));
            return java.util.Collections.singletonList(
                    attendanceMapper.mapToAttendanceResponse(att, user, clinic, null, null, null));
        }
    }

    // Lấy lịch sử chấm công của user (phân trang)
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

        return attendances.map(att -> {
            User user = userRepo.findById(att.getUserId()).orElse(null);
            Clinic clinic = clinicRepo.findById(att.getClinicId()).orElse(null);
            return attendanceMapper.mapToAttendanceResponse(att, user, clinic, null, null, null);
        });
    }

    // Thống kê tổng hợp dữ liệu chấm công trong 1 khoảng thời gian
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAttendanceStatistics(
            Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate) {
        log.info("Calculating attendance statistics for userId: {}, clinicId: {}, period: {} to {}",
                userId, clinicId, startDate, endDate);

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
            if (status == null) {
                //
            } else if ("ON_TIME".equals(status) || "APPROVED_PRESENT".equals(status)) {
                presentCount++;
            } else if ("LATE".equals(status) || "APPROVED_LATE".equals(status)) {
                lateCount++;
                presentCount++;
            } else if ("ABSENT".equals(status)) {
                absentCount++;
            } else if ("APPROVED_ABSENCE".equals(status)) {
                leaveCount++;
            }

            if (attendance.getActualWorkHours() != null) {
                totalHours = totalHours.add(attendance.getActualWorkHours());
                recordsWithHours++;
            } else if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                long minutes = java.time.Duration.between(
                        attendance.getCheckInTime(),
                        attendance.getCheckOutTime()).toMinutes();
                BigDecimal hours = BigDecimal.valueOf(minutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
                totalHours = totalHours.add(hours);
                recordsWithHours++;
            }
        }

        BigDecimal averageHours = BigDecimal.ZERO;
        if (recordsWithHours > 0) {
            averageHours = totalHours.divide(
                    BigDecimal.valueOf(recordsWithHours),
                    2,
                    java.math.RoundingMode.HALF_UP);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("presentCount", presentCount);
        stats.put("lateCount", lateCount);
        stats.put("absentCount", absentCount);
        stats.put("leaveCount", leaveCount);
        stats.put("totalHours", totalHours.doubleValue());
        stats.put("averageHours", averageHours.doubleValue());

        log.info("Attendance statistics calculated: totalRecords={}, presentCount={}, lateCount={}, " +
                "absentCount={}, leaveCount={}, totalHours={}, averageHours={}",
                totalRecords, presentCount, lateCount, absentCount, leaveCount,
                totalHours, averageHours);

        return stats;
    }

    // Lấy 1 bản ghi attendance theo id
    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getAttendanceById(Integer attendanceId) {
        Attendance attendance = attendanceRepo.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        EmployeeFaceProfile faceProfile = faceProfileRepo.findByUserId(attendance.getUserId()).orElse(null);

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, null, null);
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

    // Danh sách chấm công cho admin lọc theo trạng thái
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceForAdmin(LocalDate workDate, Integer clinicId, String status) {
        LocalDate targetDate = workDate != null ? workDate : LocalDate.now();
        String normalizedStatus = (status != null && !status.trim().isEmpty())
                ? status.trim().toUpperCase()
                : null;
        if (normalizedStatus != null && !ADMIN_ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new AttendanceValidationException("Unsupported attendanceStatus: " + normalizedStatus);
        }
        List<Attendance> attendances;
        if (clinicId != null && normalizedStatus != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDateAndAttendanceStatus(
                    clinicId, targetDate, normalizedStatus);
        } else if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDate(clinicId, targetDate);
        } else if (normalizedStatus != null) {
            attendances = attendanceRepo.findByWorkDateAndAttendanceStatus(targetDate, normalizedStatus);
        } else {
            attendances = attendanceRepo.findByWorkDate(targetDate);
        }

        return attendances.stream()
                .map(this::mapToResponseWithLookups)
                .collect(Collectors.toList());
    }

    // Admin cập nhật trạng thái attendance (approve/deny) chấm công
    @Override
    @Transactional
    public AttendanceResponse updateAttendanceStatus(Integer attendanceId, String newStatus, String adminNote,
            Integer adminUserId) {
        Attendance attendance = attendanceRepo.findById(attendanceId)
                .orElseThrow(() -> new AttendanceNotFoundException(attendanceId));
        if (newStatus == null || newStatus.trim().isEmpty()) {
            throw new AttendanceValidationException("newStatus is required");
        }
        String normalizedStatus = newStatus.trim().toUpperCase();
        if (!ADMIN_ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new AttendanceValidationException("Unsupported attendanceStatus: " + normalizedStatus);
        }

        attendance.setAttendanceStatus(normalizedStatus);

        if (adminNote != null && !adminNote.trim().isEmpty()) {
            StringBuilder noteBuilder = new StringBuilder();
            if (attendance.getNote() != null && !attendance.getNote().trim().isEmpty()) {
                noteBuilder.append(attendance.getNote().trim()).append("\n");
            }
            String approver = null;
            if (adminUserId != null) {
                approver = userRepo.findById(adminUserId)
                        .map(User::getFullName)
                        .orElse(null);
            }
            noteBuilder.append("[Approved");
            if (approver != null && !approver.isBlank()) {
                noteBuilder.append(" by ").append(approver);
            }
            noteBuilder.append("] ").append(adminNote.trim());
            attendance.setNote(noteBuilder.toString());
        }

        attendance.setUpdatedAt(Instant.now());
        Attendance saved = attendanceRepo.save(attendance);

        return mapToResponseWithLookups(saved);
    }

    // Lấy danh sách attendance cần giải trình cho nhân viên
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> getAttendanceNeedingExplanation(Integer userId) {
        return attendanceExplanationHelper.getAttendanceNeedingExplanation(userId);
    }

    // Nộp giải trình cho một bản ghi attendance
    @Override
    @Transactional
    public AttendanceResponse submitExplanation(AttendanceExplanationRequest request) {
        Attendance attendance = attendanceExplanationHelper.submitExplanation(request);
        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        // Send Notification to HR users
        try {
            List<Integer> hrUserIds = getHrUserIds();
            log.info("Found {} HR users to notify about explanation submission for attendance {}",
                    hrUserIds.size(), attendance.getId());

            if (hrUserIds.isEmpty()) {
                log.warn("No active HR users found to send notification about explanation submission");
            }

            String explanationTypeLabel = getExplanationTypeLabel(request.getExplanationType());

            for (Integer hrUserId : hrUserIds) {
                try {
                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(hrUserId)
                            .type("EXPLANATION_SUBMITTED")
                            .priority("MEDIUM")
                            .title("New Attendance Explanation")
                            .message(String.format("Employee %s has submitted a %s explanation for %s.",
                                    user.getFullName(), explanationTypeLabel, attendance.getWorkDate()))
                            .relatedEntityType("ATTENDANCE")
                            .relatedEntityId(attendance.getId())
                            .actionUrl("/hr/attendance/explanations")
                            .build();
                    notificationService.sendNotification(notiRequest);
                    log.info("Notification sent successfully to HR user {} about new explanation for attendance {}",
                            hrUserId, attendance.getId());
                } catch (Exception e) {
                    log.error("Failed to send notification to HR user {}: {}", hrUserId, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send explanation submission notifications: {}", e.getMessage(), e);
        }

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, null, null, null);
    }

    // Lấy danh sách attendance đang chờ giải trình (HR)
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> getPendingExplanations(Integer clinicId) {
        return attendanceExplanationHelper.getPendingExplanations(clinicId);
    }

    // Xét duyệt/reject giải trình chấm công
    @Override
    @Transactional
    public AttendanceResponse processExplanation(AdminExplanationActionRequest request, Integer adminUserId) {
        Attendance attendance = attendanceExplanationHelper.processExplanation(request, adminUserId);
        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));
        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, null, null, null);
    }

    // mapping attendance + lookup các thông tin liên quan
    private AttendanceResponse mapToResponseWithLookups(Attendance attendance) {
        User user = userRepo.findById(attendance.getUserId()).orElse(null);
        Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, null, null, null);
    }

    // Xác định trạng thái ON_TIME/LATE của điểm danh bác sĩ dựa vào ca & lịch
    private String determineAttendanceStatusForDoctor(
            Integer userId,
            Integer clinicId,
            LocalDate workDate,
            Instant checkInTime,
            String shiftType) {

        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

        Optional<DoctorSchedule> matchedSchedule = shiftService.findMatchingSchedule(userId, clinicId, workDate,
                shiftType);
        DoctorSchedule schedule = matchedSchedule.orElse(null);
        LocalTime expectedStartTime = shiftService.getExpectedStartTime(shiftType, schedule);

        // Kiểm tra xem có đơn nghỉ phép đã duyệt cho ngày này không
        boolean hasApprovedLeave = attendanceStatusCalculator.hasApprovedLeaveOnDate(userId, workDate);

        if (checkInLocalTime.isBefore(expectedStartTime) || checkInLocalTime.equals(expectedStartTime)) {
            log.info("Doctor {} checked in ON_TIME for {} shift: {} (expected: {})",
                    userId, shiftType, checkInLocalTime, expectedStartTime);
            return "ON_TIME";
        } else {
            // Nếu có đơn nghỉ phép đã duyệt, coi như APPROVED_LATE thay vì LATE
            if (hasApprovedLeave) {
                long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                log.info(
                        "Doctor {} checked in APPROVED_LATE with approved leave for {} shift: {} minutes late (expected: {}, actual: {})",
                        userId, shiftType, minutesLate, expectedStartTime, checkInLocalTime);
                return "APPROVED_LATE";
            } else {
                long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                log.info("Doctor {} checked in LATE for {} shift: {} minutes late (expected: {}, actual: {})",
                        userId, shiftType, minutesLate, expectedStartTime, checkInLocalTime);
                return "LATE";
            }
        }
    }

    // Lấy danh sách HR user IDs (tương tự LeaveRequestServiceImpl)
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

    // Lấy label cho explanation type
    private String getExplanationTypeLabel(String type) {
        if (type == null)
            return "Unknown";
        switch (type) {
            case "LATE_CHECKIN":
                return "Late Check-in";
            case "EARLY_CHECKOUT":
                return "Early Check-out";
            case "ABSENCE":
                return "Absence";
            case "MISSING_CHECKOUT":
                return "Missing Check-out";
            default:
                return type;
        }
    }

}
