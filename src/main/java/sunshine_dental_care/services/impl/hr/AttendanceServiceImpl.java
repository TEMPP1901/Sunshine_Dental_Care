package sunshine_dental_care.services.impl.hr;

import java.math.BigDecimal;
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

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
            "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE", "APPROVED_LATE", "APPROVED_PRESENT"
    );

    // Hours allowed for check-in/check-out
    private static final LocalTime CHECK_IN_START_TIME = LocalTime.of(8, 0);
    private static final LocalTime CHECK_IN_END_TIME = LocalTime.of(11, 0);
    private static final LocalTime CHECK_OUT_START_TIME = LocalTime.of(15, 0);
    private static final LocalTime CHECK_OUT_END_TIME = LocalTime.of(19, 0);

    // Xử lý check-in cho nhân viên
    @Override
    @Transactional
    public AttendanceResponse checkIn(AttendanceCheckInRequest request) {
        log.info("Processing check-in for user {} (clinicId: {})",
                request.getUserId(), request.getClinicId() != null ? request.getClinicId() : "will be resolved");
        LocalDate today = LocalDate.now();
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

        Optional<Attendance> existing = attendanceRepo
                .findByUserIdAndClinicIdAndWorkDate(request.getUserId(), clinicId, today);

        if (existing.isPresent() && existing.get().getCheckInTime() != null) {
            throw new AlreadyCheckedInException(
                    String.format("User %d already checked in at clinic %d on %s",
                            request.getUserId(), clinicId, today));
        }

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        if (currentTime.isBefore(CHECK_IN_START_TIME) || currentTime.isAfter(CHECK_IN_END_TIME)) {
            throw new AttendanceValidationException(
                    String.format("Check-in allowed only from %s to %s. Current time: %s",
                            CHECK_IN_START_TIME, CHECK_IN_END_TIME, currentTime));
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

        Attendance attendance;
        if (existing.isPresent()) {
            attendance = existing.get();
        } else {
            attendance = new Attendance();
            attendance.setUserId(request.getUserId());
            attendance.setClinicId(clinicId);
            attendance.setWorkDate(today);
        }

        Instant checkInTime = Instant.now();
        attendance.setCheckInTime(checkInTime);
        attendance.setCheckInMethod("FACE_WIFI");
        attendance.setNote(request.getNote());

        attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));

        // Nội dung: Nếu xác thực khuôn mặt và wifi đều hợp lệ thì accepted, ngược lại failed
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

        // Tính trạng thái attendance (ON_TIME/LATE)
        // CHỈ set status nếu chưa có status được approve (APPROVED_LATE, APPROVED_ABSENCE, APPROVED_PRESENT)
        // HOẶC chưa có explanation đã được approve (kiểm tra note có [APPROVED])
        // Để tránh ghi đè status đã được admin approve
        String currentStatus = attendance.getAttendanceStatus();
        String currentNote = attendance.getNote();
        boolean hasApprovedExplanation = currentNote != null && currentNote.contains("[APPROVED]");
        
        if (currentStatus == null || 
            (!"APPROVED_LATE".equals(currentStatus) && 
             !"APPROVED_ABSENCE".equals(currentStatus) && 
             !"APPROVED_PRESENT".equals(currentStatus) && 
             !hasApprovedExplanation)) {
            String attendanceStatus = attendanceStatusCalculator.determineAttendanceStatus(
                    request.getUserId(),
                    clinicId,
                    today,
                    checkInTime
            );
            attendance.setAttendanceStatus(attendanceStatus);
            log.debug("Set attendance status to {} for attendance {}", attendanceStatus, attendance.getId());
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

        LocalTime currentTime = LocalTime.now(ZoneId.systemDefault());
        if (currentTime.isBefore(CHECK_OUT_START_TIME) || currentTime.isAfter(CHECK_OUT_END_TIME)) {
            throw new AttendanceValidationException(
                    String.format("Check-out is allowed only from %s to %s. Current time: %s",
                            CHECK_OUT_START_TIME, CHECK_OUT_END_TIME, currentTime));
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

        attendance.setCheckOutTime(Instant.now());

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

        // Xác thực thành công chỉ khi xác thực khuôn mặt và wifi đều hợp lệ
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

        // Nếu sau check-out mà vẫn chưa có check-in thì status phải là ABSENT
        // CHỈ set ABSENT nếu chưa có status được approve (APPROVED_ABSENCE, APPROVED_PRESENT)
        // HOẶC chưa có explanation đã được approve (kiểm tra note có [APPROVED])
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
        Optional<Attendance> attendance = attendanceRepo.findByUserIdAndWorkDate(userId, today);
        // Không có attendance hôm nay là trường hợp hợp lệ, không phải lỗi
        // Trả về null thay vì throw exception
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

        Map<String, Object> stats = new HashMap<>();
        // TODO: implement statistics such as total attendances, on-time/late, avg. work hours...
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

    // Mapping Attendance entity to AttendanceResponse DTO
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

    // Lấy danh sách chấm công cho admin lọc theo trạng thái
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

    // Cập nhật trạng thái chấm công (ADMIN duyệt)
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

    // Lấy danh sách attendance cần giải trình cho nhân viên (30 ngày)
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

        // Chỉ validate attendance tồn tại, quyền kiểm tra ở controller

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

    // Xử lý duyệt/reject giải trình attendance (admin)
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
                // Nếu chỉ có check-out (không có check-in) và được approve
                // → APPROVED_PRESENT (có mặt, chỉ quên check-in)
                newStatus = "APPROVED_PRESENT";
                attendance.setAttendanceStatus(newStatus);
                log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_IN → APPROVED_PRESENT", attendance.getId());
            } else if ("MISSING_CHECK_OUT".equalsIgnoreCase(explanationType)) {
                // Nếu chỉ có check-in (không có check-out) và được approve
                // Cập nhật status dựa trên status hiện tại:
                // - Nếu LATE → APPROVED_LATE
                // - Nếu ON_TIME → APPROVED_PRESENT (có mặt đúng giờ)
                if ("LATE".equalsIgnoreCase(oldStatus)) {
                    newStatus = "APPROVED_LATE";
                    attendance.setAttendanceStatus(newStatus);
                    log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_OUT with LATE → APPROVED_LATE", attendance.getId());
                } else if ("ON_TIME".equalsIgnoreCase(oldStatus)) {
                    newStatus = "APPROVED_PRESENT";
                    attendance.setAttendanceStatus(newStatus);
                    log.info("Explanation APPROVED for attendance {}: MISSING_CHECK_OUT with ON_TIME → APPROVED_PRESENT", attendance.getId());
                } else {
                    // Nếu status khác (null hoặc status lạ), giữ nguyên
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

    // Xác định chấm công nào cần gửi giải trình
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

    // Mapping sang attendance explanation response
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
}
