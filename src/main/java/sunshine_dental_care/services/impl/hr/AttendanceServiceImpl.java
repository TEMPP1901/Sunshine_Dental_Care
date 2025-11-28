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
import sunshine_dental_care.services.interfaces.hr.AttendanceService;
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

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
            "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE", "APPROVED_LATE", "APPROVED_PRESENT"
    );

    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
    private static final LocalTime MORNING_SHIFT_END = LocalTime.of(11, 0);
    private static final LocalTime AFTERNOON_SHIFT_START = LocalTime.of(13, 0);
    private static final LocalTime EMPLOYEE_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime EMPLOYEE_END_TIME = LocalTime.of(18, 0);
    private static final BigDecimal EMPLOYEE_EXPECTED_HOURS = BigDecimal.valueOf(8.0);

    // Threshold for being late: if more than 2 hours (120 minutes) late without check-in, count at least as 2 hours late
    private static final int MAX_LATE_MINUTES_THRESHOLD = 120;

    // Xử lý check-in cho nhân viên hoặc bác sĩ, tính trạng thái, cập nhật lịch bác sĩ
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
        String shiftType;
        Attendance attendance;

        if (isDoctor) {
            shiftType = determineShiftForDoctor(currentTime);
            Optional<Attendance> existingShift = attendanceRepo
                    .findByUserIdAndClinicIdAndWorkDateAndShiftType(
                        request.getUserId(), clinicId, today, shiftType);
            if (existingShift.isPresent() && existingShift.get().getCheckInTime() != null) {
                throw new AlreadyCheckedInException(
                        String.format("Doctor %d already checked in for %s shift at clinic %d on %s",
                                request.getUserId(), shiftType, clinicId, today));
            }
            attendance = existingShift.orElseGet(() -> {
                Attendance att = new Attendance();
                att.setUserId(request.getUserId());
                att.setClinicId(clinicId);
                att.setWorkDate(today);
                att.setShiftType(shiftType);
                return att;
            });
            if ("AFTERNOON".equals(shiftType) && currentTime.isBefore(AFTERNOON_SHIFT_START)) {
                throw new AttendanceValidationException(
                        String.format("Cannot check-in for AFTERNOON shift before 13:00. Current time: %s", currentTime));
            }
        } else {
            shiftType = "FULL_DAY";
            Optional<Attendance> existing = attendanceRepo
                    .findByUserIdAndClinicIdAndWorkDate(request.getUserId(), clinicId, today);
            if (existing.isPresent() && existing.get().getCheckInTime() != null) {
                throw new AlreadyCheckedInException(
                        String.format("Employee %d already checked in at clinic %d on %s",
                                request.getUserId(), clinicId, today));
            }
            attendance = existing.orElseGet(() -> {
                Attendance att = new Attendance();
                att.setUserId(request.getUserId());
                att.setClinicId(clinicId);
                att.setWorkDate(today);
                att.setShiftType("FULL_DAY");
                return att;
            });
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
            bssid
        );

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        Instant checkInTime = Instant.now();
        attendance.setCheckInTime(checkInTime);
        attendance.setCheckInMethod("FACE_WIFI");
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(request.getNote());
        }
        attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));

        // Tính số phút đi trễ khi check-in
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();
        LocalTime expectedStartTime;

        if (isDoctor && shiftType != null) {
            // Lấy giờ bắt đầu ca cho bác sĩ dựa trên lịch hoặc giờ mặc định
            var schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDateAllStatus(
                    request.getUserId(), clinicId, today);
            LocalTime scheduleStartTime = null;
            DoctorSchedule matchedSchedule = null;
            if (!schedules.isEmpty()) {
                for (var schedule : schedules) {
                    LocalTime startTime = schedule.getStartTime();
                    boolean isMorningMatch = "MORNING".equals(shiftType) && startTime.isBefore(LUNCH_BREAK_START);
                    boolean isAfternoonMatch = "AFTERNOON".equals(shiftType) && startTime.isAfter(LUNCH_BREAK_START);
                    if (isMorningMatch || isAfternoonMatch) {
                        scheduleStartTime = schedule.getStartTime();
                        matchedSchedule = schedule;
                        break;
                    }
                }
                if (scheduleStartTime == null) {
                    var firstSchedule = schedules.stream()
                            .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()))
                            .orElse(null);
                    if (firstSchedule != null) {
                        scheduleStartTime = firstSchedule.getStartTime();
                        matchedSchedule = firstSchedule;
                    }
                }
            }
            // Nếu có matched schedule thì set lại active
            if (matchedSchedule != null) {
                if (matchedSchedule.getStatus() == null || !"ACTIVE".equals(matchedSchedule.getStatus())) {
                    matchedSchedule.setStatus("ACTIVE");
                    doctorScheduleRepo.save(matchedSchedule);
                }
                // Nếu check-in ca MORNING thì set active tất cả schedule AF trong ngày cho doctor
                if ("MORNING".equals(shiftType)) {
                    var allSchedules = doctorScheduleRepo.findByDoctorIdAndWorkDate(request.getUserId(), today);
                    for (var schedule : allSchedules) {
                        if (schedule == null || schedule.getId().equals(matchedSchedule.getId())) continue;
                        LocalTime scheduleStart = schedule.getStartTime();
                        if (scheduleStart == null || !scheduleStart.isAfter(LUNCH_BREAK_START)) continue;
                        if (schedule.getStatus() == null || !"ACTIVE".equals(schedule.getStatus())) {
                            schedule.setStatus("ACTIVE");
                            doctorScheduleRepo.save(schedule);
                        }
                    }
                }
            }
            if (scheduleStartTime != null) {
                expectedStartTime = scheduleStartTime;
            } else {
                if ("MORNING".equals(shiftType)) {
                    expectedStartTime = LocalTime.of(8, 0);
                } else if ("AFTERNOON".equals(shiftType)) {
                    expectedStartTime = LocalTime.of(13, 0);
                } else {
                    expectedStartTime = EMPLOYEE_START_TIME;
                }
            }
        } else {
            // Nhân viên dùng giờ mặc định
            expectedStartTime = EMPLOYEE_START_TIME;
        }

        if (checkInLocalTime.isAfter(expectedStartTime)) {
            long actualMinutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
            long minutesLate = actualMinutesLate >= MAX_LATE_MINUTES_THRESHOLD ? MAX_LATE_MINUTES_THRESHOLD : actualMinutesLate;
            attendance.setLateMinutes((int) minutesLate);
        } else {
            attendance.setLateMinutes(0);
        }

        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        attendance.setVerificationStatus(faceVerified && wifiValid ? "VERIFIED" : "FAILED");

        // Chỉ update trạng thái attendance nếu chưa có trạng thái admin duyệt
        String currentStatus = attendance.getAttendanceStatus();
        String currentNote = attendance.getNote();
        boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");
        boolean isApprovedStatus = currentStatus != null && (
                "APPROVED_LATE".equals(currentStatus) ||
                "APPROVED_ABSENCE".equals(currentStatus) ||
                "APPROVED_PRESENT".equals(currentStatus) ||
                hasApprovedExplanation
        );

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
            if ("MORNING".equals(shiftType)) {
                if (currentTime.isBefore(LocalTime.of(8, 0))) {
                    throw new AttendanceValidationException(
                        String.format("Cannot check-out for MORNING shift before 8:00. Current time: %s", currentTime));
                }
            } else if ("AFTERNOON".equals(shiftType)) {
                if (currentTime.isBefore(AFTERNOON_SHIFT_START)) {
                    throw new AttendanceValidationException(
                        String.format("Cannot check-out for AFTERNOON shift before 13:00. Current time: %s", currentTime));
                }
            }
        } else {
            if (currentTime.isBefore(LUNCH_BREAK_END)) {
                throw new AttendanceValidationException(
                        String.format("Check-out is allowed only after lunch break (after 13:00). Current time: %s", currentTime));
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
            bssid
        );

        EmployeeFaceProfile faceProfile = verification.getFaceProfile();
        FaceVerificationResult faceResult = verification.getFaceResult();
        WiFiValidationResult wifiResult = verification.getWifiResult();

        Instant checkOutTime = Instant.now();
        attendance.setCheckOutTime(checkOutTime);

        // Tính toán giờ công thực tế, số phút đi trễ/về sớm và nghỉ trưa
        if (attendance.getCheckInTime() != null && checkOutTime != null) {
            LocalTime checkInLocalTime = attendance.getCheckInTime().atZone(ZoneId.systemDefault()).toLocalTime();
            LocalTime checkOutLocalTime = checkOutTime.atZone(ZoneId.systemDefault()).toLocalTime();

            LocalTime expectedStartTime;
            LocalTime expectedEndTime;
            BigDecimal expectedWorkHours;

            if (isDoctor) {
                var schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                        attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate());
                LocalTime scheduleStartTime = null;
                LocalTime scheduleEndTime = null;
                String attendanceShiftType = attendance.getShiftType();
                if (!schedules.isEmpty()) {
                    if (attendanceShiftType != null) {
                        for (var schedule : schedules) {
                            LocalTime startTime = schedule.getStartTime();
                            if ("MORNING".equals(attendanceShiftType) && startTime.isBefore(LUNCH_BREAK_START)) {
                                scheduleStartTime = schedule.getStartTime();
                                scheduleEndTime = schedule.getEndTime();
                                break;
                            } else if ("AFTERNOON".equals(attendanceShiftType) && startTime.isAfter(LUNCH_BREAK_START)) {
                                scheduleStartTime = schedule.getStartTime();
                                scheduleEndTime = schedule.getEndTime();
                                break;
                            }
                        }
                    }
                    if (scheduleStartTime == null) {
                        var firstSchedule = schedules.stream()
                                .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()))
                                .orElse(null);
                        if (firstSchedule != null) {
                            scheduleStartTime = firstSchedule.getStartTime();
                            scheduleEndTime = firstSchedule.getEndTime();
                        }
                    }
                }
                if (scheduleStartTime == null) {
                    if (attendanceShiftType != null) {
                        if ("MORNING".equals(attendanceShiftType)) {
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(11, 0);
                        } else if ("AFTERNOON".equals(attendanceShiftType)) {
                            expectedStartTime = LocalTime.of(13, 0);
                            expectedEndTime = LocalTime.of(18, 0);
                        } else {
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(18, 0);
                        }
                    } else {
                        if (checkInLocalTime.isBefore(LUNCH_BREAK_START)) {
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(11, 0);
                        } else if (checkInLocalTime.isAfter(LUNCH_BREAK_END)) {
                            expectedStartTime = LocalTime.of(13, 0);
                            expectedEndTime = LocalTime.of(18, 0);
                        } else {
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(11, 0);
                        }
                    }
                } else {
                    expectedStartTime = scheduleStartTime;
                    expectedEndTime = scheduleEndTime;
                }

                long expectedMinutes = java.time.Duration.between(expectedStartTime, expectedEndTime).toMinutes();
                expectedWorkHours = BigDecimal.valueOf(expectedMinutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            } else {
                expectedStartTime = EMPLOYEE_START_TIME;
                expectedEndTime = EMPLOYEE_END_TIME;
                expectedWorkHours = EMPLOYEE_EXPECTED_HOURS;
            }
            attendance.setExpectedWorkHours(expectedWorkHours);

            long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), checkOutTime).toMinutes();

            // Chỉ tính lại lateMinutes nếu chưa có từ check-in
            long minutesLate;
            if (attendance.getLateMinutes() != null) {
                minutesLate = attendance.getLateMinutes();
            } else {
                if (checkInLocalTime.isAfter(expectedStartTime)) {
                    long actualMinutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                    minutesLate = actualMinutesLate >= MAX_LATE_MINUTES_THRESHOLD
                            ? MAX_LATE_MINUTES_THRESHOLD
                            : actualMinutesLate;
                    attendance.setLateMinutes((int) minutesLate);
                } else {
                    minutesLate = 0;
                    attendance.setLateMinutes(0);
                }
            }

            if (checkOutLocalTime.isBefore(expectedEndTime)) {
                long minutesEarly = java.time.Duration.between(checkOutLocalTime, expectedEndTime).toMinutes();
                attendance.setEarlyMinutes((int) minutesEarly);
            } else {
                attendance.setEarlyMinutes(0);
            }

            long lunchBreakMinutes = 0;
            if (!isDoctor && checkInLocalTime.isBefore(LUNCH_BREAK_START) && checkOutLocalTime.isAfter(LUNCH_BREAK_END)) {
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
        }

        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(
                    (attendance.getNote() != null ? attendance.getNote() + " | " : "") + request.getNote()
            );
        }

        if (attendance.getFaceMatchScore() == null ||
                faceResult.getSimilarityScore() > attendance.getFaceMatchScore().doubleValue()) {
            attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));
        }

        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        attendance.setVerificationStatus(faceVerified && wifiValid ? "VERIFIED" : "FAILED");

        attendance = attendanceRepo.save(attendance);

        // Nếu check-out mà vẫn chưa có check-in thì set trạng thái absent nếu không phải đã được duyệt
        if (attendance.getCheckInTime() == null) {
            String currentStatus = attendance.getAttendanceStatus();
            String currentNote = attendance.getNote();
            boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");
            boolean isApprovedStatus = "APPROVED_ABSENCE".equals(currentStatus) ||
                    "APPROVED_PRESENT".equals(currentStatus) ||
                    hasApprovedExplanation;

            if (!isApprovedStatus) {
                attendance.setAttendanceStatus("ABSENT");
                attendance = attendanceRepo.save(attendance);
            }
        }

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }

    // Lấy bản ghi attendance hôm nay cho user theo role
    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(Integer userId) {
        LocalDate today = LocalDate.now();
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream().anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        if (isDoctor) {
            List<Attendance> attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
            if (attendances.isEmpty()) {
                return null;
            }
            Attendance att = attendances.stream()
                    .min((a1, a2) -> {
                        String s1 = a1.getShiftType() != null ? a1.getShiftType() : "";
                        String s2 = a2.getShiftType() != null ? a2.getShiftType() : "";
                        if ("MORNING".equals(s1) && "AFTERNOON".equals(s2)) return -1;
                        if ("AFTERNOON".equals(s1) && "MORNING".equals(s2)) return 1;
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
                if ("MORNING".equals(s1) && "AFTERNOON".equals(s2)) return -1;
                if ("AFTERNOON".equals(s1) && "MORNING".equals(s2)) return 1;
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
                        attendance.getCheckOutTime()
                ).toMinutes();
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
                    java.math.RoundingMode.HALF_UP
            );
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
    public AttendanceResponse updateAttendanceStatus(Integer attendanceId, String newStatus, String adminNote, Integer adminUserId) {
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
        return attendanceMapper.mapToAttendanceResponse(attendance, user, clinic, null, null, null);
    }

    // Lấy danh sách attendance đang chờ giải trình (admin)
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

    // Xác định ca cho bác sĩ dựa vào giờ hiện tại
    private String determineShiftForDoctor(LocalTime currentTime) {
        if (currentTime.isBefore(LUNCH_BREAK_START)) {
            return "MORNING";
        } else if (currentTime.isBefore(LUNCH_BREAK_END)) {
            throw new AttendanceValidationException(
                    String.format("Cannot check-in during lunch break (11:00-13:00). Current time: %s", currentTime));
        } else {
            return "AFTERNOON";
        }
    }

    // Xác định trạng thái ON_TIME/LATE của điểm danh bác sĩ dựa vào ca & lịch
    private String determineAttendanceStatusForDoctor(
            Integer userId,
            Integer clinicId,
            LocalDate workDate,
            Instant checkInTime,
            String shiftType) {

        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();

        var schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
        LocalTime expectedStartTime = null;

        if (!schedules.isEmpty()) {
            for (var schedule : schedules) {
                LocalTime startTime = schedule.getStartTime();
                if ("MORNING".equals(shiftType) && startTime.isBefore(LUNCH_BREAK_START)) {
                    expectedStartTime = startTime;
                    break;
                } else if ("AFTERNOON".equals(shiftType) && startTime.isAfter(LUNCH_BREAK_START)) {
                    expectedStartTime = startTime;
                    break;
                }
            }
            if (expectedStartTime == null) {
                var firstSchedule = schedules.stream()
                        .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()))
                        .orElse(null);
                if (firstSchedule != null) {
                    expectedStartTime = firstSchedule.getStartTime();
                }
            }
        }
        if (expectedStartTime == null) {
            switch (shiftType) {
                case "MORNING":
                    expectedStartTime = LocalTime.of(8, 0);
                    break;
                case "AFTERNOON":
                    expectedStartTime = LocalTime.of(13, 0);
                    break;
                default:
                    expectedStartTime = LocalTime.of(8, 0);
                    break;
            }
            log.warn("Doctor {} has no schedule for {} shift at clinic {} on {}. Using default start time: {}",
                    userId, shiftType, clinicId, workDate, expectedStartTime);
        }

        if (checkInLocalTime.isBefore(expectedStartTime) || checkInLocalTime.equals(expectedStartTime)) {
            log.info("Doctor {} checked in ON_TIME for {} shift: {} (expected: {})",
                    userId, shiftType, checkInLocalTime, expectedStartTime);
            return "ON_TIME";
        } else {
            long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
            log.info("Doctor {} checked in LATE for {} shift: {} minutes late (expected: {}, actual: {})",
                    userId, shiftType, minutesLate, expectedStartTime, checkInLocalTime);
            return "LATE";
        }
    }
}
