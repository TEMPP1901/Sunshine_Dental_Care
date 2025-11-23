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
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
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
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
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
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final UserRoleRepo userRoleRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
            "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE", "APPROVED_LATE", "APPROVED_PRESENT"
    );

        // Thời gian check-in/check-out được hệ thống chấp nhận (đã được thay thế bằng EMPLOYEE_START_TIME, EMPLOYEE_END_TIME)
    
    // Giờ nghỉ trưa: 11:00-13:00
    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);
    private static final LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
    
    // Ca sáng: 08:00-11:00, Ca chiều: 13:00-18:00
    private static final LocalTime MORNING_SHIFT_END = LocalTime.of(11, 0);
    private static final LocalTime AFTERNOON_SHIFT_START = LocalTime.of(13, 0);
    
    // Giờ làm việc cho nhân viên: 08:00-18:00 (trừ 2h nghỉ trưa = 8 giờ)
    private static final LocalTime EMPLOYEE_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime EMPLOYEE_END_TIME = LocalTime.of(18, 0);
    private static final BigDecimal EMPLOYEE_EXPECTED_HOURS = BigDecimal.valueOf(8.0); // 10 giờ - 2 giờ nghỉ trưa

    // Xử lý check-in cho nhân viên
    @Override
    @Transactional
    public AttendanceResponse checkIn(AttendanceCheckInRequest request) {
        log.info("Processing check-in for user {} (clinicId: {})",
                request.getUserId(), request.getClinicId() != null ? request.getClinicId() : "will be resolved");
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new AttendanceValidationException("Attendance is not allowed on Sundays");
        }
        attendanceStatusCalculator.validateUserRoleForAttendance(request.getUserId());

        Integer clinicId = request.getClinicId();
        if (clinicId == null) {
            log.info("ClinicId not provided in request, resolving for user {}", request.getUserId());

            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(request.getUserId());
            if (assignments != null && !assignments.isEmpty()) {
                Optional<UserClinicAssignment> primaryAssignment = assignments.stream()
                        .filter(a -> a != null && Boolean.TRUE.equals(a.getIsPrimary()))
                        .findFirst();

                UserClinicAssignment selectedAssignment;
                if (primaryAssignment.isPresent()) {
                    selectedAssignment = primaryAssignment.get();
                    log.debug("Found primary assignment for user {}", request.getUserId());
                } else {
                    selectedAssignment = assignments.get(0);
                    log.debug("Using first assignment (no primary found) for user {}", request.getUserId());
                }

                Clinic clinic = selectedAssignment.getClinic();
                if (clinic != null) {
                    clinicId = clinic.getId();
                    if (clinicId != null) {
                        log.info("Resolved clinicId {} from UserClinicAssignment for user {} (total assignments: {})",
                                clinicId, request.getUserId(), assignments.size());
                    }
                }
            }

            if (clinicId == null) {
                log.info("No UserClinicAssignment found, trying to get clinicId from UserRole for user {}", request.getUserId());
                List<UserRole> userRoles = userRoleRepo.findActiveByUserId(request.getUserId());

                if (userRoles != null && !userRoles.isEmpty()) {
                    for (UserRole userRole : userRoles) {
                        if (userRole == null) continue;
                        try {
                            Clinic clinic = userRole.getClinic();
                            if (clinic != null) {
                                clinicId = clinic.getId();
                                if (clinicId != null) {
                                    log.info("Resolved clinicId {} from UserRole.clinic for user {}", clinicId, request.getUserId());
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Could not load clinic from UserRole entity: {}", e.getMessage());
                        }
                    }
                }
            }

            if (clinicId == null) {
                log.warn("User {} has no clinic assignment. Trying to use first available clinic as fallback.", request.getUserId());
                try {
                    List<Clinic> allClinics = clinicRepo.findAll();
                    if (allClinics != null && !allClinics.isEmpty()) {
                        clinicId = allClinics.get(0).getId();
                        log.warn("Using first clinic (ID: {}) as fallback for user {}. Please assign user to a clinic properly.",
                                clinicId, request.getUserId());
                    }
                } catch (Exception e) {
                    log.error("Failed to get clinics for fallback: {}", e.getMessage());
                }
            }

            if (clinicId == null) {
                throw new AttendanceValidationException(
                        String.format("Cannot resolve clinicId for user %d. No clinic assignment, role with clinic, or clinics in system found. Please assign user to a clinic.",
                                request.getUserId()));
            }
        }

        // Kiểm tra role của user để xác định logic chấm công
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(request.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        String shiftType;
        Attendance attendance;

        if (isDoctor) {
            // BÁC SĨ: Chấm công theo ca (MORNING/AFTERNOON)
            shiftType = determineShiftForDoctor(currentTime);
            log.info("Doctor {} checking in for {} shift at {}", request.getUserId(), shiftType, currentTime);

            // Tìm attendance theo shiftType
            Optional<Attendance> existingShift = attendanceRepo
                    .findByUserIdAndClinicIdAndWorkDateAndShiftType(
                            request.getUserId(), clinicId, today, shiftType);

            if (existingShift.isPresent() && existingShift.get().getCheckInTime() != null) {
                throw new AlreadyCheckedInException(
                        String.format("Doctor %d already checked in for %s shift at clinic %d on %s",
                                request.getUserId(), shiftType, clinicId, today));
            }

            if (existingShift.isPresent()) {
                attendance = existingShift.get();
            } else {
                attendance = new Attendance();
                attendance.setUserId(request.getUserId());
                attendance.setClinicId(clinicId);
                attendance.setWorkDate(today);
                attendance.setShiftType(shiftType);
            }

            // Validate thời gian check-in theo ca
            if ("MORNING".equals(shiftType)) {
                if (currentTime.isAfter(MORNING_SHIFT_END)) {
                    log.warn("Doctor {} attempting check-in for MORNING shift after 11:00. Current time: {}", 
                            request.getUserId(), currentTime);
                }
            } else if ("AFTERNOON".equals(shiftType)) {
                if (currentTime.isBefore(AFTERNOON_SHIFT_START)) {
                    throw new AttendanceValidationException(
                            String.format("Cannot check-in for AFTERNOON shift before 13:00. Current time: %s", currentTime));
                }
            }
        } else {
            // NHÂN VIÊN: Chấm công cả ngày (FULL_DAY)
            shiftType = "FULL_DAY";
            log.info("Employee {} checking in for FULL_DAY at {}", request.getUserId(), currentTime);

            // Tìm attendance FULL_DAY (chỉ cho phép 1 record/ngày)
            Optional<Attendance> existing = attendanceRepo
                    .findByUserIdAndClinicIdAndWorkDate(request.getUserId(), clinicId, today);

            if (existing.isPresent() && existing.get().getCheckInTime() != null) {
                throw new AlreadyCheckedInException(
                        String.format("Employee %d already checked in at clinic %d on %s",
                                request.getUserId(), clinicId, today));
            }

            if (existing.isPresent()) {
                attendance = existing.get();
                // Đảm bảo shiftType là FULL_DAY
                if (attendance.getShiftType() == null || !"FULL_DAY".equals(attendance.getShiftType())) {
                    attendance.setShiftType("FULL_DAY");
                }
            } else {
                attendance = new Attendance();
                attendance.setUserId(request.getUserId());
                attendance.setClinicId(clinicId);
                attendance.setWorkDate(today);
                attendance.setShiftType("FULL_DAY");
            }

            // Validate thời gian check-in cho nhân viên
            // Cho phép check-in từ 8:00 trở đi (không giới hạn thời gian cuối)
            // Nếu check-in sau 8:00 sẽ bị trừ giờ đi trễ khi tính lương
            if (currentTime.isBefore(EMPLOYEE_START_TIME)) {
                log.warn("Employee {} attempting check-in before 8:00. Current time: {}. Allowing check-in but will be marked as LATE if after 8:00.",
                        request.getUserId(), currentTime);
            } else if (currentTime.isAfter(LUNCH_BREAK_START) && currentTime.isBefore(LUNCH_BREAK_END)) {
                log.warn("Employee {} attempting check-in during lunch break (11:00-13:00). Current time: {}. Allowing check-in.",
                        request.getUserId(), currentTime);
            }
        }

        String ssid = request.getSsid();
        String bssid = request.getBssid();

        boolean isEmulatorWiFi = (ssid != null && (ssid.trim().equalsIgnoreCase("AndroidWifi") ||
                ssid.trim().equalsIgnoreCase("AndroidAP") ||
                ssid.trim().toUpperCase().startsWith("ANDROID"))) ||
                (bssid != null && bssid.trim().startsWith("00:13:10"));

        if (isEmulatorWiFi || ((ssid == null || ssid.trim().isEmpty()) && (bssid == null || bssid.trim().isEmpty()))) {
            log.info("Detected emulator WiFi or no WiFi from mobile (SSID={}, BSSID={}). Getting WiFi info from host machine (Windows)",
                    ssid, bssid);
            try {
                sunshine_dental_care.utils.WindowsWiFiUtil.WiFiInfo hostWiFi =
                        sunshine_dental_care.utils.WindowsWiFiUtil.getCurrentWiFiInfo();
                if (hostWiFi.getSsid() != null && !hostWiFi.getSsid().trim().isEmpty()) {
                    ssid = hostWiFi.getSsid();
                    log.info("Using host machine SSID: {}", ssid);
                }
                if (hostWiFi.getBssid() != null && !hostWiFi.getBssid().trim().isEmpty()) {
                    bssid = hostWiFi.getBssid();
                    log.info("Using host machine BSSID: {}", bssid);
                }
            } catch (Exception e) {
                log.warn("Failed to get WiFi info from host machine: {}", e.getMessage());
            }
        }

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

        // attendance đã được tạo/lấy ở trên (theo shiftType)
        Instant checkInTime = Instant.now();
        attendance.setCheckInTime(checkInTime);
        attendance.setCheckInMethod("FACE_WIFI");
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(request.getNote());
        }

        attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));

        // Nếu xác thực khuôn mặt và wifi đều hợp lệ thì Accepted, ngược lại Failed
        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        boolean fullyVerified = faceVerified && wifiValid;
        attendance.setVerificationStatus(fullyVerified ? "VERIFIED" : "FAILED");

        if (fullyVerified) {
            log.info("Check-in SUCCESS for user {} at clinic {}: Face verified={} (similarity={} >= 0.8), WiFi valid={} (SSID={}). VerificationStatus=VERIFIED",
                    request.getUserId(), clinicId, faceVerified,
                    String.format("%.4f", faceResult.getSimilarityScore()), wifiValid, ssid);
        } else {
            String failureReasons = "";
            if (!faceVerified) {
                failureReasons += String.format("Face similarity %.4f < 0.8", faceResult.getSimilarityScore());
            }
            if (!wifiValid) {
                if (!failureReasons.isEmpty()) failureReasons += ", ";
                failureReasons += String.format("WiFi not in whitelist (SSID=%s, BSSID=%s)", ssid, bssid);
            }
            log.warn("Check-in FAILED for user {} at clinic {}: Face verified={} (similarity={}), WiFi valid={} (SSID={}). Reasons: {}. VerificationStatus=FAILED",
                    request.getUserId(), clinicId, faceVerified,
                    String.format("%.4f", faceResult.getSimilarityScore()), wifiValid, ssid, failureReasons);
        }

        // Tính trạng thái attendance (ON_TIME/LATE), chỉ ghi đè nếu chưa có status duyệt
        String currentStatus = attendance.getAttendanceStatus();
        String currentNote = attendance.getNote();
        boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");

        if (currentStatus == null ||
                (!"APPROVED_LATE".equals(currentStatus) &&
                !"APPROVED_ABSENCE".equals(currentStatus) &&
                !"APPROVED_PRESENT".equals(currentStatus) &&
                !hasApprovedExplanation)) {
            // Xác định trạng thái dựa vào role và shiftType
            String attendanceStatus;
            if (isDoctor && shiftType != null) {
                // BÁC SĨ: Xác định status theo ca và schedule
                attendanceStatus = determineAttendanceStatusForDoctor(
                        request.getUserId(),
                        clinicId,
                        today,
                        checkInTime,
                        shiftType
                );
            } else {
                // NHÂN VIÊN: Xác định status theo giờ mặc định
                attendanceStatus = attendanceStatusCalculator.determineAttendanceStatus(
                        request.getUserId(),
                        clinicId,
                        today,
                        checkInTime
                );
            }
            attendance.setAttendanceStatus(attendanceStatus);
            log.debug("Set attendance status to {} for attendance {} (role: {}, shiftType: {})", 
                    attendanceStatus, attendance.getId(), isDoctor ? "DOCTOR" : "EMPLOYEE", shiftType);
        } else {
            log.info("Preserving approved status {} for attendance {} (not overwriting with check-in status. Has approved explanation: {})",
                    currentStatus, attendance.getId(), hasApprovedExplanation);
        }

        attendance = attendanceRepo.save(attendance);

        User user = userRepo.findById(request.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        log.info("Check-in successful for user {} at clinic {}: attendanceId={}, verificationStatus={}, faceSimilarity={}",
                request.getUserId(), clinicId, attendance.getId(), attendance.getVerificationStatus(),
                String.format("%.4f", faceResult.getSimilarityScore()));

        return mapToResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }

    // Xử lý check-out cho nhân viên
    @Override
    @Transactional
    public AttendanceResponse checkOut(AttendanceCheckOutRequest request) {
        log.info("Processing check-out for attendance {}", request.getAttendanceId());

        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        if (attendance.getCheckInTime() == null) {
            throw new AttendanceValidationException("Cannot check out without check-in");
        }
        if (attendance.getCheckOutTime() != null) {
            throw new AttendanceValidationException("Already checked out");
        }

        attendanceStatusCalculator.validateUserRoleForAttendance(attendance.getUserId());

        // Kiểm tra role để validate thời gian check-out
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(attendance.getUserId());
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        String shiftType = attendance.getShiftType();

        if (isDoctor && shiftType != null) {
            // BÁC SĨ: Validate theo ca
            // Cho phép check-out sau khi bắt đầu ca (không giới hạn thời gian cuối)
            // expectedEndTime sẽ được lấy từ schedule khi tính giờ, nếu check-out trước expectedEndTime sẽ bị trừ giờ ra sớm
            if ("MORNING".equals(shiftType)) {
                // Ca sáng: Cho phép check-out sau khi bắt đầu ca (8:00)
                if (currentTime.isBefore(LocalTime.of(8, 0))) {
                    throw new AttendanceValidationException(
                            String.format("Cannot check-out for MORNING shift before 8:00. Current time: %s", currentTime));
                }
                // Nếu check-out trước expectedEndTime (từ schedule, thường là 11:00) sẽ bị trừ giờ ra sớm khi tính lương
            } else if ("AFTERNOON".equals(shiftType)) {
                // Ca chiều: Cho phép check-out sau khi bắt đầu ca (13:00)
                if (currentTime.isBefore(AFTERNOON_SHIFT_START)) {
                    throw new AttendanceValidationException(
                            String.format("Cannot check-out for AFTERNOON shift before 13:00. Current time: %s", currentTime));
                }
                // Nếu check-out trước expectedEndTime (từ schedule, thường là 18:00) sẽ bị trừ giờ ra sớm khi tính lương
            }
        } else {
            // NHÂN VIÊN: Check-out từ sau giờ nghỉ trưa (13:00) trở đi
            // ExpectedEndTime là 18:00, nếu check-out trước 18:00 sẽ bị trừ giờ ra sớm khi tính lương
            if (currentTime.isBefore(LUNCH_BREAK_END)) {
                throw new AttendanceValidationException(
                        String.format("Check-out is allowed only after lunch break (after 13:00). Current time: %s", currentTime));
            }
            // Cho phép check-out sau 13:00 (không giới hạn thời gian cuối)
            // Nếu check-out trước 18:00 sẽ bị trừ giờ ra sớm khi tính lương
        }

        String ssid = request.getSsid();
        String bssid = request.getBssid();

        boolean isEmulatorWiFi = (ssid != null && (ssid.trim().equalsIgnoreCase("AndroidWifi") ||
                ssid.trim().equalsIgnoreCase("AndroidAP") ||
                ssid.trim().toUpperCase().startsWith("ANDROID"))) ||
                (bssid != null && bssid.trim().startsWith("00:13:10"));

        if (isEmulatorWiFi || ((ssid == null || ssid.trim().isEmpty()) && (bssid == null || bssid.trim().isEmpty()))) {
            log.info("Detected emulator WiFi or no WiFi from mobile for check-out (SSID={}, BSSID={}). Getting WiFi info from host machine (Windows)",
                    ssid, bssid);
            try {
                sunshine_dental_care.utils.WindowsWiFiUtil.WiFiInfo hostWiFi =
                        sunshine_dental_care.utils.WindowsWiFiUtil.getCurrentWiFiInfo();
                if (hostWiFi.getSsid() != null && !hostWiFi.getSsid().trim().isEmpty()) {
                    ssid = hostWiFi.getSsid();
                    log.info("Using host machine SSID for check-out: {}", ssid);
                }
                if (hostWiFi.getBssid() != null && !hostWiFi.getBssid().trim().isEmpty()) {
                    bssid = hostWiFi.getBssid();
                    log.info("Using host machine BSSID for check-out: {}", bssid);
                }
            } catch (Exception e) {
                log.warn("Failed to get WiFi info from host machine for check-out: {}", e.getMessage());
            }
        }

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

        // Tính số giờ làm việc thực tế (actualWorkHours) từ check-in đến check-out, trừ giờ đi trễ và ra sớm
        if (attendance.getCheckInTime() != null && checkOutTime != null) {
            LocalTime checkInLocalTime = attendance.getCheckInTime().atZone(ZoneId.systemDefault()).toLocalTime();
            LocalTime checkOutLocalTime = checkOutTime.atZone(ZoneId.systemDefault()).toLocalTime();
            
            // Xác định expectedStartTime, expectedEndTime, expectedWorkHours
            LocalTime expectedStartTime;
            LocalTime expectedEndTime;
            BigDecimal expectedWorkHours;
            
            // Sử dụng lại biến isDoctor đã được khai báo ở trên
            
            if (isDoctor) {
                // BÁC SĨ: Lấy từ schedule hoặc dùng mặc định
                var schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                        attendance.getUserId(), attendance.getClinicId(), attendance.getWorkDate());
                
                LocalTime scheduleStartTime = null;
                LocalTime scheduleEndTime = null;
                String attendanceShiftType = attendance.getShiftType();
                
                if (!schedules.isEmpty()) {
                    if (attendanceShiftType != null) {
                        // Có shiftType: Tìm schedule phù hợp với shiftType
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
                    
                    // Nếu không tìm thấy schedule phù hợp với shiftType, hoặc shiftType = null
                    // Lấy schedule đầu tiên (sớm nhất trong ngày)
                    if (scheduleStartTime == null) {
                        var firstSchedule = schedules.stream()
                                .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()))
                                .orElse(null);
                        if (firstSchedule != null) {
                            scheduleStartTime = firstSchedule.getStartTime();
                            scheduleEndTime = firstSchedule.getEndTime();
                            log.info("Doctor {} has no shiftType or no matching schedule, using first schedule: {} - {}",
                                    attendance.getUserId(), scheduleStartTime, scheduleEndTime);
                        }
                    }
                }
                
                // Nếu không có schedule, dùng giờ mặc định
                if (scheduleStartTime == null) {
                    if (attendanceShiftType != null) {
                        // Có shiftType: Dùng mặc định theo ca
                        if ("MORNING".equals(attendanceShiftType)) {
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(11, 0);
                        } else if ("AFTERNOON".equals(attendanceShiftType)) {
                            expectedStartTime = LocalTime.of(13, 0);
                            expectedEndTime = LocalTime.of(18, 0);
                        } else {
                            // shiftType khác (FULL_DAY hoặc giá trị không hợp lệ)
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(18, 0);
                        }
                    } else {
                        // Không có shiftType: Suy luận từ thời gian check-in
                        if (checkInLocalTime.isBefore(LUNCH_BREAK_START)) {
                            // Check-in trước 11:00 → Ca sáng
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(11, 0);
                            log.info("Doctor {} has no shiftType, inferred MORNING shift from check-in time: {}",
                                    attendance.getUserId(), checkInLocalTime);
                        } else if (checkInLocalTime.isAfter(LUNCH_BREAK_END)) {
                            // Check-in sau 13:00 → Ca chiều
                            expectedStartTime = LocalTime.of(13, 0);
                            expectedEndTime = LocalTime.of(18, 0);
                            log.info("Doctor {} has no shiftType, inferred AFTERNOON shift from check-in time: {}",
                                    attendance.getUserId(), checkInLocalTime);
                        } else {
                            // Check-in trong giờ nghỉ trưa (11:00-13:00) → Dùng ca sáng mặc định
                            expectedStartTime = LocalTime.of(8, 0);
                            expectedEndTime = LocalTime.of(11, 0);
                            log.warn("Doctor {} checked in during lunch break ({}), using MORNING shift default",
                                    attendance.getUserId(), checkInLocalTime);
                        }
                    }
                } else {
                    // Có schedule: Dùng từ schedule
                    expectedStartTime = scheduleStartTime;
                    expectedEndTime = scheduleEndTime;
                }
                
                // Tính expectedWorkHours từ startTime và endTime
                long expectedMinutes = java.time.Duration.between(expectedStartTime, expectedEndTime).toMinutes();
                expectedWorkHours = BigDecimal.valueOf(expectedMinutes)
                        .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            } else {
                // NHÂN VIÊN: 8h-18h (trừ 2h nghỉ trưa = 8 giờ)
                expectedStartTime = EMPLOYEE_START_TIME;
                expectedEndTime = EMPLOYEE_END_TIME;
                expectedWorkHours = EMPLOYEE_EXPECTED_HOURS;
            }
            
            // Set expectedWorkHours
            attendance.setExpectedWorkHours(expectedWorkHours);
            
            // Tính tổng thời gian từ check-in đến check-out (phút)
            long totalMinutes = java.time.Duration.between(attendance.getCheckInTime(), checkOutTime).toMinutes();
            
            // Trừ giờ đi trễ (nếu check-in sau expectedStartTime)
            long minutesLate = 0;
            if (checkInLocalTime.isAfter(expectedStartTime)) {
                minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
                log.info("User {} checked in LATE: {} minutes late (expected: {}, actual: {})",
                        attendance.getUserId(), minutesLate, expectedStartTime, checkInLocalTime);
            }
            
            // Trừ giờ ra sớm (nếu check-out trước expectedEndTime)
            long minutesEarly = 0;
            if (checkOutLocalTime.isBefore(expectedEndTime)) {
                minutesEarly = java.time.Duration.between(checkOutLocalTime, expectedEndTime).toMinutes();
                log.info("User {} checked out EARLY: {} minutes early (expected: {}, actual: {})",
                        attendance.getUserId(), minutesEarly, expectedEndTime, checkOutLocalTime);
            }
            
            // Trừ giờ nghỉ trưa cho nhân viên (2 giờ = 120 phút)
            // Chỉ trừ nếu check-in trước 11:00 và check-out sau 13:00
            long lunchBreakMinutes = 0;
            if (!isDoctor) {
                if (checkInLocalTime.isBefore(LUNCH_BREAK_START) && checkOutLocalTime.isAfter(LUNCH_BREAK_END)) {
                    lunchBreakMinutes = 120; // 2 giờ nghỉ trưa
                    log.info("Employee {} worked through lunch break, subtracting 120 minutes (2 hours)",
                            attendance.getUserId());
                }
            }
            
            // Tính actualWorkHours = tổng thời gian - giờ đi trễ - giờ ra sớm - giờ nghỉ trưa
            long adjustedMinutes = totalMinutes - minutesLate - minutesEarly - lunchBreakMinutes;
            // Đảm bảo không âm
            if (adjustedMinutes < 0) {
                adjustedMinutes = 0;
                log.warn("Adjusted minutes is negative for attendance {}, setting to 0", attendance.getId());
            }
            
            BigDecimal actualHours = BigDecimal.valueOf(adjustedMinutes)
                    .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
            attendance.setActualWorkHours(actualHours);
            
            log.info("Calculated actualWorkHours for attendance {}: {} hours (total: {} min, late: {} min, early: {} min, lunch: {} min, adjusted: {} min, expected: {} hours)", 
                    attendance.getId(), actualHours, totalMinutes, minutesLate, minutesEarly, lunchBreakMinutes, adjustedMinutes, expectedWorkHours);
        }

        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(
                    (attendance.getNote() != null ? attendance.getNote() + " | " : "") + request.getNote()
            );
        }

        // Nếu điểm nhận diện khuôn mặt check-out tốt hơn, cập nhật lại
        if (attendance.getFaceMatchScore() == null ||
                faceResult.getSimilarityScore() > attendance.getFaceMatchScore().doubleValue()) {
            attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));
        }

        // Chỉ xác thực thành công nếu cả khuôn mặt và wifi đều hợp lệ
        boolean faceVerified = faceResult.isVerified();
        boolean wifiValid = wifiResult.isValid();
        boolean fullyVerified = faceVerified && wifiValid;
        attendance.setVerificationStatus(fullyVerified ? "VERIFIED" : "FAILED");

        if (fullyVerified) {
            log.info("Check-out SUCCESS for attendance {}: Face verified={} (similarity={} >= 0.8), WiFi valid={} (SSID={}). VerificationStatus=VERIFIED",
                    attendance.getId(), faceVerified, String.format("%.4f", faceResult.getSimilarityScore()), wifiValid, ssid);
        } else {
            String failureReasons = "";
            if (!faceVerified) {
                failureReasons += String.format("Face similarity %.4f < 0.8", faceResult.getSimilarityScore());
            }
            if (!wifiValid) {
                if (!failureReasons.isEmpty()) failureReasons += ", ";
                failureReasons += String.format("WiFi not in whitelist (SSID=%s, BSSID=%s)", ssid, bssid);
            }
            log.warn("Check-out FAILED for attendance {}: Face verified={} (similarity={}), WiFi valid={} (SSID={}). Reasons: {}. VerificationStatus=FAILED",
                    attendance.getId(), faceVerified, String.format("%.4f", faceResult.getSimilarityScore()), wifiValid, ssid, failureReasons);
        }

        attendance = attendanceRepo.save(attendance);

        // Nếu sau check-out vẫn chưa có check-in thì status là ABSENT (trừ phi được duyệt)
        if (attendance.getCheckInTime() == null) {
            String currentStatus = attendance.getAttendanceStatus();
            String currentNote = attendance.getNote();
            boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");

            if (!"APPROVED_ABSENCE".equals(currentStatus) &&
                !"APPROVED_PRESENT".equals(currentStatus) &&
                !hasApprovedExplanation) {
                attendance.setAttendanceStatus("ABSENT");
                attendance = attendanceRepo.save(attendance);
                log.warn("Check-out without check-in for attendance {}. Status set to ABSENT.", attendance.getId());
            } else {
                log.info("Preserving approved status {} for attendance {} (check-out without check-in but already approved. Has approved explanation: {})",
                        currentStatus, attendance.getId(), hasApprovedExplanation);
            }
        }

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        log.info("Check-out successful for attendance {}: userId={}, clinicId={}, verificationStatus={}, attendanceStatus={}",
                attendance.getId(), attendance.getUserId(), attendance.getClinicId(),
                attendance.getVerificationStatus(), attendance.getAttendanceStatus());

        return mapToResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(Integer userId) {
        LocalDate today = LocalDate.now();
        
        // Kiểm tra role để quyết định logic
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        if (isDoctor) {
            // BÁC SĨ: Lấy tất cả các ca trong ngày, trả về ca đầu tiên (hoặc null nếu không có)
            List<Attendance> attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
            if (attendances.isEmpty()) {
                return null;
            }
            // Trả về ca đầu tiên (sắp xếp theo thời gian check-in hoặc shiftType)
            Attendance att = attendances.stream()
                    .min((a1, a2) -> {
                        // Ưu tiên MORNING trước, sau đó AFTERNOON
                        String s1 = a1.getShiftType() != null ? a1.getShiftType() : "";
                        String s2 = a2.getShiftType() != null ? a2.getShiftType() : "";
                        if ("MORNING".equals(s1) && "AFTERNOON".equals(s2)) return -1;
                        if ("AFTERNOON".equals(s1) && "MORNING".equals(s2)) return 1;
                        // Nếu cùng loại, sắp xếp theo checkInTime
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
            
            return mapToResponse(att, user, clinic, null, null, null);
        } else {
            // NHÂN VIÊN: Chỉ có 1 attendance record/ngày
            Optional<Attendance> attendance = attendanceRepo.findByUserIdAndWorkDate(userId, today);
            if (attendance.isEmpty()) {
                return null;
            }

            Attendance att = attendance.get();
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
            Clinic clinic = clinicRepo.findById(att.getClinicId())
                    .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

            return mapToResponse(att, user, clinic, null, null, null);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getTodayAttendanceList(Integer userId) {
        LocalDate today = LocalDate.now();
        
        // Kiểm tra role để quyết định logic
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> attendanceStatusCalculator.isDoctorRole(ur));

        if (isDoctor) {
            // BÁC SĨ: Lấy tất cả các ca trong ngày
            List<Attendance> attendances = attendanceRepo.findAllByUserIdAndWorkDate(userId, today);
            if (attendances.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            
            // Sắp xếp: MORNING trước, sau đó AFTERNOON
            attendances.sort((a1, a2) -> {
                String s1 = a1.getShiftType() != null ? a1.getShiftType() : "";
                String s2 = a2.getShiftType() != null ? a2.getShiftType() : "";
                if ("MORNING".equals(s1) && "AFTERNOON".equals(s2)) return -1;
                if ("AFTERNOON".equals(s1) && "MORNING".equals(s2)) return 1;
                // Nếu cùng loại, sắp xếp theo checkInTime
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
                        return mapToResponse(att, user, clinic, null, null, null);
                    })
                    .collect(java.util.stream.Collectors.toList());
        } else {
            // NHÂN VIÊN: Chỉ có 1 attendance record/ngày
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
                    mapToResponse(att, user, clinic, null, null, null));
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

        return attendances.map(att -> {
            User user = userRepo.findById(att.getUserId()).orElse(null);
            Clinic clinic = clinicRepo.findById(att.getClinicId()).orElse(null);
            return mapToResponse(att, user, clinic, null, null, null);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAttendanceStatistics(
            Integer userId, Integer clinicId, LocalDate startDate, LocalDate endDate) {
        log.info("Calculating attendance statistics for userId: {}, clinicId: {}, period: {} to {}",
                userId, clinicId, startDate, endDate);

        // Query attendances trong khoảng thời gian
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

        // Khởi tạo counters
        int totalRecords = attendances.size();
        int presentCount = 0;
        int lateCount = 0;
        int absentCount = 0;
        int leaveCount = 0;
        BigDecimal totalHours = BigDecimal.ZERO;
        int recordsWithHours = 0; // Đếm số bản ghi có giờ làm việc để tính trung bình

        // Duyệt qua từng attendance để tính toán
        for (Attendance attendance : attendances) {
            String status = attendance.getAttendanceStatus();
            
            // Đếm theo status
            if (status == null) {
                // Không có status, bỏ qua
            } else if ("ON_TIME".equals(status) || "APPROVED_PRESENT".equals(status)) {
                presentCount++;
            } else if ("LATE".equals(status) || "APPROVED_LATE".equals(status)) {
                lateCount++;
                presentCount++; // Late vẫn được tính là có mặt
            } else if ("ABSENT".equals(status)) {
                absentCount++;
            } else if ("APPROVED_ABSENCE".equals(status)) {
                leaveCount++;
            }

            // Tính tổng giờ làm việc
            // Ưu tiên sử dụng actualWorkHours (đã được tính khi check-out)
            if (attendance.getActualWorkHours() != null) {
                totalHours = totalHours.add(attendance.getActualWorkHours());
                recordsWithHours++;
            } else if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                // Fallback: tính từ checkIn/checkOut nếu actualWorkHours chưa có
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

        // Tính trung bình giờ làm việc
        BigDecimal averageHours = BigDecimal.ZERO;
        if (recordsWithHours > 0) {
            averageHours = totalHours.divide(
                    BigDecimal.valueOf(recordsWithHours), 
                    2, 
                    java.math.RoundingMode.HALF_UP
            );
        }

        // Tạo Map kết quả
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", totalRecords);
        stats.put("presentCount", presentCount);
        stats.put("lateCount", lateCount);
        stats.put("absentCount", absentCount);
        stats.put("leaveCount", leaveCount);
        stats.put("totalHours", totalHours.doubleValue()); // Convert BigDecimal to double cho frontend
        stats.put("averageHours", averageHours.doubleValue()); // Convert BigDecimal to double cho frontend

        log.info("Attendance statistics calculated: totalRecords={}, presentCount={}, lateCount={}, " +
                "absentCount={}, leaveCount={}, totalHours={}, averageHours={}",
                totalRecords, presentCount, lateCount, absentCount, leaveCount, 
                totalHours, averageHours);

        return stats;
    }

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

        return mapToResponse(attendance, user, clinic, faceProfile, null, null);
    }

    // Mapping Attendance entity sang AttendanceResponse DTO
    private AttendanceResponse mapToResponse(
            Attendance attendance,
            User user,
            Clinic clinic,
            EmployeeFaceProfile faceProfile,
            FaceVerificationResult faceResult,
            WiFiValidationResult wifiResult) {
        AttendanceResponse response = new AttendanceResponse();
        response.setId(attendance.getId());
        response.setUserId(attendance.getUserId());
        response.setUserName(user != null ? user.getFullName() : null);
        response.setUserAvatarUrl(user != null ? user.getAvatarUrl() : null);
        response.setFaceImageUrl(user != null ? user.getAvatarUrl() : null);
        response.setClinicId(attendance.getClinicId());
        response.setClinicName(clinic != null ? clinic.getClinicName() : null);
        response.setWorkDate(attendance.getWorkDate());
        response.setCheckInTime(attendance.getCheckInTime());
        response.setCheckOutTime(attendance.getCheckOutTime());
        response.setCheckInMethod(attendance.getCheckInMethod());
        response.setDeviceId(attendance.getDeviceId());
        response.setIpAddr(attendance.getIpAddr());
        response.setSsid(attendance.getSsid());
        response.setBssid(attendance.getBssid());
        response.setLat(attendance.getLat());
        response.setLng(attendance.getLng());
        response.setIsOvertime(attendance.getIsOvertime());
        response.setNote(attendance.getNote());
        response.setFaceMatchScore(attendance.getFaceMatchScore());
        response.setVerificationStatus(attendance.getVerificationStatus());
        response.setAttendanceStatus(attendance.getAttendanceStatus());
        response.setShiftType(attendance.getShiftType());

        if (wifiResult != null) {
            response.setWifiValid(wifiResult.isValid());
            response.setWifiValidationMessage(wifiResult.getMessage());
        }

        response.setCreatedAt(attendance.getCreatedAt());
        response.setUpdatedAt(attendance.getUpdatedAt());

        return response;
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

    // Lấy danh sách attendance cho admin lọc theo trạng thái
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

    // Cập nhật trạng thái chấm công (admin duyệt)
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

    // Lấy danh sách attendance cần giải trình cho nhân viên (trong 30 ngày gần nhất)
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> getAttendanceNeedingExplanation(Integer userId) {
        log.info("Getting attendance needing explanation for user {}", userId);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<Attendance> attendances = attendanceRepo.findByUserIdAndWorkDateBetweenOrderByWorkDateDesc(
                userId, startDate, endDate);

        return attendances.stream()
                .filter(att -> needsExplanation(att))
                .map(att -> mapToExplanationResponse(att))
                .collect(Collectors.toList());
    }

    // Nộp giải trình cho một bản chấm công
    @Override
    @Transactional
    public AttendanceResponse submitExplanation(AttendanceExplanationRequest request) {
        log.info("Submitting explanation for attendance {}", request.getAttendanceId());

        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        String note = String.format("[EXPLANATION_REQUEST:%s] %s",
                request.getExplanationType().toUpperCase(), request.getReason());

        if (attendance.getNote() != null && !attendance.getNote().trim().isEmpty()) {
            attendance.setNote(attendance.getNote() + "\n" + note);
        } else {
            attendance.setNote(note);
        }

        attendance = attendanceRepo.save(attendance);

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        log.info("Explanation submitted for attendance {}: type={}",
                attendance.getId(), request.getExplanationType());

        return mapToResponse(attendance, user, clinic, null, null, null);
    }

    // Lấy những bản attendance cần kiểm tra giải trình (admin)
    @Override
    @Transactional(readOnly = true)
    public List<AttendanceExplanationResponse> getPendingExplanations(Integer clinicId) {
        log.info("Getting pending explanations for clinic {}", clinicId);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<Attendance> attendances;
        if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDateBetween(
                    clinicId, startDate, endDate, PageRequest.of(0, 1000)).getContent();
        } else {
            attendances = attendanceRepo.findByWorkDateBetween(startDate, endDate);
        }

        return attendances.stream()
                .filter(att -> hasPendingExplanation(att))
                .map(att -> mapToExplanationResponse(att))
                .collect(Collectors.toList());
    }

    // Duyệt hoặc reject giải trình chấm công (admin)
    @Override
    @Transactional
    public AttendanceResponse processExplanation(AdminExplanationActionRequest request, Integer adminUserId) {
        log.info("Processing explanation for attendance {}: action={}",
                request.getAttendanceId(), request.getAction());

        Attendance attendance = attendanceRepo.findById(request.getAttendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(request.getAttendanceId()));

        String note = attendance.getNote();
        if (note == null || !note.contains("[EXPLANATION_REQUEST:")) {
            throw new AttendanceValidationException("No pending explanation found for this attendance");
        }

        String explanationType = extractExplanationType(note);
        String employeeReason = extractEmployeeReason(note);

        User adminUser = userRepo.findById(adminUserId)
                .orElseThrow(() -> new AttendanceNotFoundException("Admin user not found"));

        String adminName = adminUser.getFullName() != null ? adminUser.getFullName() : "Admin";

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            String approvedNote = String.format("[APPROVED] %s", employeeReason);
            if (request.getAdminNote() != null && !request.getAdminNote().trim().isEmpty()) {
                approvedNote += String.format(" | [Admin: %s] %s", adminName, request.getAdminNote());
            }

            note = note.replaceFirst("\\[EXPLANATION_REQUEST:[^\\]]+\\].*", approvedNote);
            attendance.setNote(note);

            // Khi duyệt – cập nhật status theo loại giải trình
            String oldStatus = attendance.getAttendanceStatus();
            String newStatus = oldStatus;

            if ("LATE".equalsIgnoreCase(explanationType)) {
                newStatus = "APPROVED_LATE";
                attendance.setAttendanceStatus(newStatus);
                log.info("Explanation APPROVED for attendance {}: LATE → APPROVED_LATE", attendance.getId());
            } else if ("ABSENT".equalsIgnoreCase(explanationType)) {
                newStatus = "APPROVED_ABSENCE";
                attendance.setAttendanceStatus(newStatus);
                log.info("Explanation APPROVED for attendance {}: {} → APPROVED_ABSENCE", attendance.getId(), explanationType);
            } else if ("MISSING_CHECK_IN".equalsIgnoreCase(explanationType)) {
                newStatus = "APPROVED_PRESENT";
                attendance.setAttendanceStatus(newStatus);
                log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_IN → APPROVED_PRESENT", attendance.getId());
            } else if ("MISSING_CHECK_OUT".equalsIgnoreCase(explanationType)) {
                // Nếu chỉ có check-in (không có check-out) và được approve
                // Cập nhật status dựa trên status hiện tại:
                // - Nếu LATE → APPROVED_LATE
                // - Nếu ON_TIME → APPROVED_PRESENT
                if ("LATE".equalsIgnoreCase(oldStatus)) {
                    newStatus = "APPROVED_LATE";
                    attendance.setAttendanceStatus(newStatus);
                    log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_OUT with LATE → APPROVED_LATE", attendance.getId());
                } else if ("ON_TIME".equalsIgnoreCase(oldStatus)) {
                    newStatus = "APPROVED_PRESENT";
                    attendance.setAttendanceStatus(newStatus);
                    log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_OUT with ON_TIME → APPROVED_PRESENT", attendance.getId());
                } else {
                    log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_OUT, keep original status {} (approved)", attendance.getId(), oldStatus);
                }
            }

            log.info("Explanation APPROVED for attendance {}: type={}, status updated from {} to {}",
                    attendance.getId(), explanationType, oldStatus, newStatus);
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            String rejectedNote = String.format("[REJECTED] %s", employeeReason);
            if (request.getAdminNote() != null && !request.getAdminNote().trim().isEmpty()) {
                rejectedNote += String.format(" | [Admin: %s] %s", adminName, request.getAdminNote());
            } else {
                rejectedNote += String.format(" | [Admin: %s] Explanation rejected", adminName);
            }
            note = note.replaceFirst("\\[EXPLANATION_REQUEST:[^\\]]+\\].*", rejectedNote);
            attendance.setNote(note);
            log.info("Explanation REJECTED for attendance {}: type={}, status Unchanged: {}",
                    attendance.getId(), explanationType, attendance.getAttendanceStatus());
        } else {
            throw new AttendanceValidationException("Invalid action. Must be APPROVE or REJECT");
        }

        attendance = attendanceRepo.save(attendance);

        User user = userRepo.findById(attendance.getUserId())
                .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
                .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));

        return mapToResponse(attendance, user, clinic, null, null, null);
    }

    // Xác định bản chấm công nào cần gửi giải trình
    private boolean needsExplanation(Attendance att) {
        // Cần giải trình nếu:
        // 1. LATE hoặc ABSENT
        // 2. Có check-in nhưng không có check-out
        // 3. Có check-out nhưng không có check-in
        // 4. Chưa có explanation request/process trong note

        if (hasPendingExplanation(att) || hasProcessedExplanation(att)) {
            return false;
        }

        String status = att.getAttendanceStatus();
        boolean hasCheckIn = att.getCheckInTime() != null;
        boolean hasCheckOut = att.getCheckOutTime() != null;

        return "LATE".equals(status) ||
                "ABSENT".equals(status) ||
                (hasCheckIn && !hasCheckOut) ||
                (!hasCheckIn && hasCheckOut);
    }

    // Kiểm tra bản ghi có giải trình đang chờ duyệt không
    private boolean hasPendingExplanation(Attendance att) {
        String note = att.getNote();
        return note != null && note.contains("[EXPLANATION_REQUEST:");
    }

    // Kiểm tra bản ghi đã được duyệt giải trình chưa
    private boolean hasProcessedExplanation(Attendance att) {
        String note = att.getNote();
        return note != null && (note.contains("[APPROVED]") || note.contains("[REJECTED]"));
    }

    // Tách loại giải trình từ note bản ghi
    private String extractExplanationType(String note) {
        if (note == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[EXPLANATION_REQUEST:([^\\]]+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(note);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // Tách lý do giải trình nhân viên ghi từ note bản ghi
    private String extractEmployeeReason(String note) {
        if (note == null) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[EXPLANATION_REQUEST:[^\\]]+\\]\\s*(.*?)(?:\\s*\\|\\s*\\[Admin:.*)?$");
        java.util.regex.Matcher matcher = pattern.matcher(note);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return note;
    }

    // Mapping sang AttendanceExplanationResponse
    private AttendanceExplanationResponse mapToExplanationResponse(Attendance attendance) {
        AttendanceExplanationResponse response = new AttendanceExplanationResponse();
        response.setAttendanceId(attendance.getId());
        response.setUserId(attendance.getUserId());
        response.setClinicId(attendance.getClinicId());
        response.setWorkDate(attendance.getWorkDate());
        response.setCheckInTime(attendance.getCheckInTime());
        response.setCheckOutTime(attendance.getCheckOutTime());
        response.setAttendanceStatus(attendance.getAttendanceStatus());
        response.setNote(attendance.getNote());

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
                if ("LATE".equals(attendance.getAttendanceStatus())) {
                    response.setExplanationType("LATE");
                } else if ("ABSENT".equals(attendance.getAttendanceStatus())) {
                    response.setExplanationType("ABSENT");
                } else if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() == null) {
                    response.setExplanationType("MISSING_CHECK_OUT");
                } else if (attendance.getCheckInTime() == null && attendance.getCheckOutTime() != null) {
                    response.setExplanationType("MISSING_CHECK_IN");
                }
                response.setExplanationStatus("PENDING");
            }
        } else {
            if ("LATE".equals(attendance.getAttendanceStatus())) {
                response.setExplanationType("LATE");
            } else if ("ABSENT".equals(attendance.getAttendanceStatus())) {
                response.setExplanationType("ABSENT");
            } else if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() == null) {
                response.setExplanationType("MISSING_CHECK_OUT");
            } else if (attendance.getCheckInTime() == null && attendance.getCheckOutTime() != null) {
                response.setExplanationType("MISSING_CHECK_IN");
            }
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

    // mapping + lookup
    private AttendanceResponse mapToResponseWithLookups(Attendance attendance) {
        User user = userRepo.findById(attendance.getUserId()).orElse(null);
        Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
        return mapToResponse(attendance, user, clinic, null, null, null);
    }

    // Xác định ca làm việc cho bác sĩ dựa vào thời gian hiện tại
    // Ca sáng: 08:00-11:00 (MORNING)
    // Nghỉ trưa: 11:00-13:00 (không cho phép check-in)
    // Ca chiều: 13:00-18:00 (AFTERNOON)
    private String determineShiftForDoctor(LocalTime currentTime) {
        if (currentTime.isBefore(LUNCH_BREAK_START)) {
            // Trước 11:00 -> Ca sáng
            return "MORNING";
        } else if (currentTime.isBefore(LUNCH_BREAK_END)) {
            // 11:00-13:00 -> Giờ nghỉ trưa, không cho phép check-in
            throw new AttendanceValidationException(
                    String.format("Cannot check-in during lunch break (11:00-13:00). Current time: %s", currentTime));
        } else {
            // Từ 13:00 trở đi -> Ca chiều
            return "AFTERNOON";
        }
    }

    // Xác định trạng thái chấm công cho bác sĩ dựa vào ca và schedule
    private String determineAttendanceStatusForDoctor(
            Integer userId,
            Integer clinicId,
            LocalDate workDate,
            Instant checkInTime,
            String shiftType) {
        
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();
        
        // Lấy schedule của bác sĩ cho ca này
        var schedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
        LocalTime expectedStartTime = null;
        
        if (!schedules.isEmpty()) {
            // Tìm schedule phù hợp với shiftType
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
            
            // Nếu không tìm thấy schedule phù hợp, lấy schedule đầu tiên
            if (expectedStartTime == null) {
                var firstSchedule = schedules.stream()
                        .min((s1, s2) -> s1.getStartTime().compareTo(s2.getStartTime()))
                        .orElse(null);
                if (firstSchedule != null) {
                    expectedStartTime = firstSchedule.getStartTime();
                }
            }
        }
        
        // Nếu không có schedule, dùng giờ mặc định theo ca
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
        
        // So sánh checkInTime với expectedStartTime
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
