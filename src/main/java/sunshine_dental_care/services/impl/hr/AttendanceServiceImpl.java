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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckInRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceCheckOutRequest;
import sunshine_dental_care.dto.hrDTO.AttendanceResponse;
import sunshine_dental_care.dto.hrDTO.DailyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.DailySummaryResponse;
import sunshine_dental_care.dto.hrDTO.MonthlyAttendanceListItemResponse;
import sunshine_dental_care.dto.hrDTO.MonthlySummaryResponse;
import sunshine_dental_care.dto.hrDTO.WiFiValidationResult;
import sunshine_dental_care.entities.Attendance;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AlreadyCheckedInException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceNotFoundException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.FaceVerificationFailedException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.WiFiValidationFailedException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DepartmentRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.services.interfaces.hr.AttendanceService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService.FaceVerificationResult;
import sunshine_dental_care.services.interfaces.hr.WiFiValidationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceServiceImpl implements AttendanceService {
    
    private final AttendanceRepository attendanceRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final DepartmentRepo departmentRepo;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final UserRoleRepo userRoleRepo;
    private final FaceRecognitionService faceRecognitionService;
    private final WiFiValidationService wifiValidationService;
    
    @Value("${app.attendance.late-threshold-minutes:15}")
    private int lateThresholdMinutes;
    
    @Value("${app.attendance.default-start-time:08:00}")
    private String defaultStartTime;
    
    // Roles không được phép chấm công
    private static final List<String> FORBIDDEN_ROLES = List.of(
        "ADMIN", "Admin", "admin",
        "PATIENT", "Patient", "patient"
    );
    
    // Role DOCTOR - check theo lịch phân công
    private static final List<String> DOCTOR_ROLE_NAMES = List.of(
        "DOCTOR", "Doctor", "doctor", "BÁC SĨ", "bác sĩ"
    );

    private static final Set<String> ADMIN_ALLOWED_STATUSES = Set.of(
        "ON_TIME", "LATE", "ABSENT", "APPROVED_ABSENCE"
    );
    
    @Override
    @Transactional
    public AttendanceResponse checkIn(AttendanceCheckInRequest request) {
        log.info("Processing check-in for user {} at clinic {}", request.getUserId(), request.getClinicId());
        
        LocalDate today = LocalDate.now();
        
        // 1. Validate user role - không cho phép ADMIN và PATIENT chấm công
        validateUserRoleForAttendance(request.getUserId());
        
        // 2. Check duplicate - đã check-in hôm nay chưa
        Optional<Attendance> existing = attendanceRepo
            .findByUserIdAndClinicIdAndWorkDate(request.getUserId(), request.getClinicId(), today);
        
        if (existing.isPresent() && existing.get().getCheckInTime() != null) {
            throw new AlreadyCheckedInException(
                String.format("User %d already checked in at clinic %d on %s", 
                    request.getUserId(), request.getClinicId(), today));
        }
        
        // 3. Verify Face - So sánh embedding với embedding đã lưu
        EmployeeFaceProfile faceProfile = faceProfileRepo
            .findByUserId(request.getUserId())
            .orElseThrow(() -> new FaceVerificationFailedException(
                "Employee face profile not found. Please register face first."));
        
        String storedEmbedding = faceProfile.getFaceEmbedding();
        if (storedEmbedding == null || storedEmbedding.trim().isEmpty()) {
            throw new FaceVerificationFailedException(
                "Face embedding not registered for this employee");
        }
        
        FaceVerificationResult faceResult;
        try {
            faceResult = faceRecognitionService.verifyFace(
                request.getFaceEmbedding(),  // Embedding từ mobile
                storedEmbedding               // Embedding đã lưu trong EmployeeFaceProfile
            );
        } catch (Exception e) {
            log.error("Face verification error: {}", e.getMessage(), e);
            throw new FaceVerificationFailedException("Face verification failed: " + e.getMessage(), e);
        }
        
        // 4. Validate WiFi - Phải đúng WiFi mới cho phép check-in
        WiFiValidationResult wifiResult = wifiValidationService.validateWiFi(
            request.getSsid(),
            request.getBssid(),
            request.getClinicId()
        );
        
        if (!wifiResult.isValid()) {
            throw new WiFiValidationFailedException(
                String.format("WiFi validation failed for clinic %d. SSID or BSSID not in whitelist", 
                    request.getClinicId()));
        }
        
        // 5. Tạo Attendance record
        Attendance attendance;
        if (existing.isPresent()) {
            attendance = existing.get();
        } else {
            attendance = new Attendance();
            attendance.setUserId(request.getUserId());
            attendance.setClinicId(request.getClinicId());
            attendance.setWorkDate(today);
        }
        
        Instant checkInTime = Instant.now();
        attendance.setCheckInTime(checkInTime);
        attendance.setSsid(request.getSsid());
        attendance.setBssid(request.getBssid());
        attendance.setCheckInMethod("FACE_WIFI");
        attendance.setNote(request.getNote());
        
        // Lưu kết quả verification
        attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));
        attendance.setVerificationStatus(
            (faceResult.isVerified() && wifiResult.isValid()) 
                ? "VERIFIED" 
                : "FAILED"
        );
        
        // 6. Xác định trạng thái chấm công (ON_TIME, LATE)
        String attendanceStatus = determineAttendanceStatus(
            request.getUserId(), 
            request.getClinicId(), 
            today, 
            checkInTime
        );
        attendance.setAttendanceStatus(attendanceStatus);
        
        attendance = attendanceRepo.save(attendance);
        
        // Load related entities for response
        User user = userRepo.findById(request.getUserId())
            .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(request.getClinicId())
            .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));
        
        log.info("Check-in successful for user {} at clinic {}: attendanceId={}, verificationStatus={}", 
            request.getUserId(), request.getClinicId(), attendance.getId(), attendance.getVerificationStatus());
        
        return mapToResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }
    
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
        
        // 1. Validate user role - không cho phép ADMIN và PATIENT chấm công
        validateUserRoleForAttendance(attendance.getUserId());
        
        // 2. Verify Face - So sánh embedding với embedding đã lưu
        EmployeeFaceProfile faceProfile = faceProfileRepo
            .findByUserId(attendance.getUserId())
            .orElseThrow(() -> new FaceVerificationFailedException(
                "Employee face profile not found. Please register face first."));
        
        String storedEmbedding = faceProfile.getFaceEmbedding();
        if (storedEmbedding == null || storedEmbedding.trim().isEmpty()) {
            throw new FaceVerificationFailedException(
                "Face embedding not registered for this employee");
        }
        
        FaceVerificationResult faceResult;
        try {
            faceResult = faceRecognitionService.verifyFace(
                request.getFaceEmbedding(),  // Embedding từ mobile
                storedEmbedding               // Embedding đã lưu trong EmployeeFaceProfile
            );
        } catch (Exception e) {
            log.error("Face verification error during check-out: {}", e.getMessage(), e);
            throw new FaceVerificationFailedException("Face verification failed: " + e.getMessage(), e);
        }
        
        // 3. Validate WiFi - Phải đúng WiFi mới cho phép check-out
        WiFiValidationResult wifiResult = wifiValidationService.validateWiFi(
            request.getSsid(),
            request.getBssid(),
            attendance.getClinicId()
        );
        
        if (!wifiResult.isValid()) {
            throw new WiFiValidationFailedException(
                String.format("WiFi validation failed for clinic %d. SSID or BSSID not in whitelist", 
                    attendance.getClinicId()));
        }
        
        // 4. Update attendance record
        attendance.setCheckOutTime(Instant.now());
        attendance.setSsid(request.getSsid());
        attendance.setBssid(request.getBssid());
        
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            attendance.setNote(
                (attendance.getNote() != null ? attendance.getNote() + " | " : "") + request.getNote()
            );
        }
        
        // Update face verification result nếu tốt hơn
        if (attendance.getFaceMatchScore() == null || 
            faceResult.getSimilarityScore() > attendance.getFaceMatchScore().doubleValue()) {
            attendance.setFaceMatchScore(BigDecimal.valueOf(faceResult.getSimilarityScore()));
        }
        
        // Update verification status nếu cả face và WiFi đều verified
        if (faceResult.isVerified() && wifiResult.isValid()) {
            attendance.setVerificationStatus("VERIFIED");
        }
        
        attendance = attendanceRepo.save(attendance);
        
        // Load related entities for response
        User user = userRepo.findById(attendance.getUserId())
            .orElseThrow(() -> new AttendanceNotFoundException("User not found"));
        Clinic clinic = clinicRepo.findById(attendance.getClinicId())
            .orElseThrow(() -> new AttendanceNotFoundException("Clinic not found"));
        
        log.info("Check-out successful for attendance {}: userId={}, clinicId={}, verificationStatus={}", 
            attendance.getId(), attendance.getUserId(), attendance.getClinicId(), attendance.getVerificationStatus());
        
        return mapToResponse(attendance, user, clinic, faceProfile, faceResult, wifiResult);
    }
    
    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(Integer userId) {
        LocalDate today = LocalDate.now();
        Optional<Attendance> attendance = attendanceRepo.findByUserIdAndWorkDate(userId, today);
        
        if (attendance.isEmpty()) {
            throw new AttendanceNotFoundException("No attendance record found for today");
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
        
        if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDateBetween(
                clinicId, startDate, endDate, pageable);
        } else {
            // TODO: Implement query for userId + date range with pagination
            // Tạm thời lấy tất cả rồi filter
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
        
        // TODO: Implement statistics calculation
        // - Total check-ins
        // - On-time vs late
        // - Average work hours
        // - etc.
        
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
    
    /**
     * Map Attendance entity to AttendanceResponse DTO
     */
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
        response.setFaceImageUrl(faceProfile != null ? faceProfile.getFaceImageUrl() : null);
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
        
        // WiFi validation results
        if (wifiResult != null) {
            response.setWifiValid(wifiResult.isValid());
            response.setWifiValidationMessage(wifiResult.getMessage());
        }
        
        response.setCreatedAt(attendance.getCreatedAt());
        response.setUpdatedAt(attendance.getUpdatedAt());
        
        return response;
    }
    
    /**
     * Validate user role - không cho phép ADMIN và PATIENT chấm công
     * Tất cả các role khác (DOCTOR, HR, RECEPTIONIST, ACCOUNTANT, NURSE, STAFF, ...) đều được phép
     * 
     * @param userId ID của user
     * @throws AttendanceValidationException nếu user có role ADMIN hoặc PATIENT
     */
    private void validateUserRoleForAttendance(Integer userId) {
        List<sunshine_dental_care.entities.UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        
        if (userRoles == null || userRoles.isEmpty()) {
            throw new AttendanceValidationException(
                "User does not have any active role. Cannot check in/out.");
        }
        
        // Kiểm tra xem user có role bị cấm không (ADMIN hoặc PATIENT)
        boolean hasForbiddenRole = userRoles.stream()
            .anyMatch(ur -> {
                if (ur == null || ur.getRole() == null) {
                    return false;
                }
                String roleName = ur.getRole().getRoleName();
                if (roleName == null || roleName.trim().isEmpty()) {
                    return false;
                }
                return FORBIDDEN_ROLES.stream()
                    .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
            });
        
        if (hasForbiddenRole) {
            throw new AttendanceValidationException(
                "Users with ADMIN or PATIENT role are not allowed to check in/out.");
        }
        
        log.debug("User {} role validated for attendance", userId);
    }
    
    /**
     * Xác định trạng thái chấm công: ON_TIME, LATE, hoặc null
     * 
     * Logic:
     * - DOCTOR: Kiểm tra theo lịch phân công của chính họ (DoctorSchedule)
     * - Các role khác (HR, RECEPTIONIST, ACCOUNTANT): 
     *   + Lấy giờ làm việc từ lịch của clinic trong ngày (DoctorSchedule của clinic)
     *   + Nếu clinic không có schedule → Dùng giờ làm việc mặc định (config)
     *   + Vì cơ sở làm việc của họ là gán cứng, họ làm theo giờ của clinic
     * 
     * @param userId ID của user
     * @param clinicId ID của clinic
     * @param workDate Ngày làm việc
     * @param checkInTime Thời gian check-in
     * @return "ON_TIME", "LATE", hoặc null
     */
    private String determineAttendanceStatus(Integer userId, Integer clinicId, 
                                              LocalDate workDate, Instant checkInTime) {
        // Lấy role của user để xác định logic
        List<sunshine_dental_care.entities.UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
            .anyMatch(ur -> {
                if (ur == null || ur.getRole() == null) {
                    return false;
                }
                String roleName = ur.getRole().getRoleName();
                return roleName != null && DOCTOR_ROLE_NAMES.stream()
                    .anyMatch(docRole -> roleName.equalsIgnoreCase(docRole));
            });
        
        LocalTime expectedStartTime;
        
        if (isDoctor) {
            // DOCTOR: Kiểm tra theo lịch phân công của chính họ
            List<sunshine_dental_care.entities.DoctorSchedule> schedules = doctorScheduleRepo
                .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
            
            if (schedules.isEmpty()) {
                // Không có schedule → Không xác định được (có thể là ngày nghỉ)
                log.debug("No schedule found for DOCTOR {} at clinic {} on {}", userId, clinicId, workDate);
                return null; // Hoặc "NO_SCHEDULE"
            }
            
            // Lấy schedule đầu tiên (thường chỉ có 1 schedule trong ngày)
            sunshine_dental_care.entities.DoctorSchedule schedule = schedules.get(0);
            expectedStartTime = schedule.getStartTime();
            log.debug("DOCTOR {} using own schedule startTime: {}", userId, expectedStartTime);
        } else {
            // Các role khác (HR, RECEPTIONIST, ACCOUNTANT): 
            // Lấy giờ làm việc từ lịch của clinic trong ngày (vì họ làm theo giờ của clinic)
            List<sunshine_dental_care.entities.DoctorSchedule> clinicSchedules = doctorScheduleRepo
                .findByClinicAndDate(clinicId, workDate);
            
            if (!clinicSchedules.isEmpty()) {
                // Clinic có schedule → Lấy startTime sớm nhất
                expectedStartTime = clinicSchedules.stream()
                    .map(sunshine_dental_care.entities.DoctorSchedule::getStartTime)
                    .min(LocalTime::compareTo)
                    .orElse(null);
                
                if (expectedStartTime == null) {
                    // Fallback nếu không parse được (không nên xảy ra)
                    log.warn("Clinic {} has schedules but cannot parse startTime. Using default.", clinicId);
                    expectedStartTime = getDefaultStartTime();
                } else {
                    log.debug("Non-DOCTOR user {} using clinic {} schedule startTime: {} (from {} schedules)", 
                        userId, clinicId, expectedStartTime, clinicSchedules.size());
                }
            } else {
                // Clinic không có schedule (ngày nghỉ) → Không xác định được attendance
                // Vì các role khác làm theo giờ của clinic, nếu clinic nghỉ thì họ cũng nghỉ
                log.debug("Clinic {} has no schedule on {} (day off). Non-DOCTOR user {} cannot determine attendance status.", 
                    clinicId, workDate, userId);
                return null; // Không xác định được (ngày nghỉ)
            }
        }
        
        // Convert checkInTime (Instant) sang LocalTime (theo timezone hệ thống)
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault())
            .toLocalTime();
        
        // So sánh với expectedStartTime
        if (checkInLocalTime.isBefore(expectedStartTime) || 
            checkInLocalTime.equals(expectedStartTime)) {
            // Check-in đúng giờ hoặc sớm
            log.debug("User {} checked in ON_TIME: {} (expected: {})", 
                userId, checkInLocalTime, expectedStartTime);
            return "ON_TIME";
        } else {
            // Check-in muộn - tính số phút muộn
            long minutesLate = java.time.Duration.between(expectedStartTime, checkInLocalTime).toMinutes();
            log.info("User {} checked in LATE: {} minutes late (expected: {}, actual: {})", 
                userId, minutesLate, expectedStartTime, checkInLocalTime);
            return "LATE";
        }
    }
    
    /**
     * Lấy giờ làm việc mặc định từ config
     * @return LocalTime mặc định (08:00 nếu config không hợp lệ)
     */
    private LocalTime getDefaultStartTime() {
        try {
            return LocalTime.parse(defaultStartTime);
        } catch (Exception e) {
            log.warn("Invalid default start time format: {}. Using 08:00 as fallback.", defaultStartTime);
            return LocalTime.of(8, 0); // Fallback: 08:00
        }
    }
    
    /**
     * Xác định vắng mặt (ABSENT) - gọi khi cần check attendance cho một ngày
     * 
     * Logic:
     * - DOCTOR: Nếu có schedule nhưng không check-in → ABSENT
     * - Các role khác: Không check ABSENT (vì không có lịch phân công cố định)
     * 
     * @param userId ID của user
     * @param clinicId ID của clinic
     * @param workDate Ngày làm việc
     */
    public void markAbsentIfNeeded(Integer userId, Integer clinicId, LocalDate workDate) {
        // Lấy role của user
        List<sunshine_dental_care.entities.UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        boolean isDoctor = userRoles != null && userRoles.stream()
            .anyMatch(ur -> {
                if (ur == null || ur.getRole() == null) {
                    return false;
                }
                String roleName = ur.getRole().getRoleName();
                return roleName != null && DOCTOR_ROLE_NAMES.stream()
                    .anyMatch(docRole -> roleName.equalsIgnoreCase(docRole));
            });
        
        if (!isDoctor) {
            // Các role khác (HR, RECEPTIONIST, ACCOUNTANT): 
            // Check ABSENT dựa trên lịch của clinic (nếu clinic có schedule nhưng không check-in)
            // Nếu clinic không có schedule (ngày nghỉ) → Không check ABSENT
            List<sunshine_dental_care.entities.DoctorSchedule> clinicSchedules = doctorScheduleRepo
                .findByClinicAndDate(clinicId, workDate);
            
            if (clinicSchedules.isEmpty()) {
                // Clinic không có schedule (ngày nghỉ) → Không check ABSENT
                log.debug("Clinic {} has no schedule on {} (day off). Non-DOCTOR user {} skipping ABSENT check", 
                    clinicId, workDate, userId);
                return;
            }
            
            // Clinic có schedule → Check ABSENT (vì họ làm theo giờ của clinic)
            // Kiểm tra có attendance record với check-in không
            Optional<Attendance> attendance = attendanceRepo
                .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
            
            if (attendance.isEmpty() || attendance.get().getCheckInTime() == null) {
                // Clinic có schedule nhưng không check-in → ABSENT
                Attendance absentRecord;
                if (attendance.isPresent()) {
                    absentRecord = attendance.get();
                } else {
                    absentRecord = new Attendance();
                    absentRecord.setUserId(userId);
                    absentRecord.setClinicId(clinicId);
                    absentRecord.setWorkDate(workDate);
                }
                absentRecord.setAttendanceStatus("ABSENT");
                attendanceRepo.save(absentRecord);
                
                log.info("Marked non-DOCTOR user {} as ABSENT at clinic {} on {} (clinic had schedule but no check-in)", 
                    userId, clinicId, workDate);
            }
            return;
        }
        
        // DOCTOR: Kiểm tra có schedule không
        List<sunshine_dental_care.entities.DoctorSchedule> schedules = doctorScheduleRepo
            .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
        
        if (schedules.isEmpty()) {
            // Không có schedule → Không phải ngày làm việc
            return;
        }
        
        // Kiểm tra có attendance record với check-in không
        Optional<Attendance> attendance = attendanceRepo
            .findByUserIdAndClinicIdAndWorkDate(userId, clinicId, workDate);
        
        if (attendance.isEmpty() || attendance.get().getCheckInTime() == null) {
            // Có schedule nhưng không check-in → ABSENT
            Attendance absentRecord;
            if (attendance.isPresent()) {
                absentRecord = attendance.get();
            } else {
                absentRecord = new Attendance();
                absentRecord.setUserId(userId);
                absentRecord.setClinicId(clinicId);
                absentRecord.setWorkDate(workDate);
            }
            absentRecord.setAttendanceStatus("ABSENT");
            attendanceRepo.save(absentRecord);
            
            log.info("Marked DOCTOR {} as ABSENT at clinic {} on {}", userId, clinicId, workDate);
        }
    }
    
    @Override
    public List<DailySummaryResponse> getDailySummary(LocalDate workDate) {
        log.info("Getting daily summary for date: {}", workDate);
        
        // Lấy tất cả departments
        List<Department> departments = departmentRepo.findAllByOrderByDepartmentNameAsc();
        List<DailySummaryResponse> summaries = new java.util.ArrayList<>();
        
        // Lấy tất cả users active (không phải ADMIN, PATIENT)
        List<User> allUsers = userRepo.findAll().stream()
            .filter(u -> u.getIsActive() != null && u.getIsActive())
            .filter(u -> {
                // Filter out ADMIN and PATIENT roles
                List<sunshine_dental_care.entities.UserRole> roles = userRoleRepo.findActiveByUserId(u.getId());
                if (roles == null || roles.isEmpty()) {
                    return false;
                }
                return roles.stream().noneMatch(ur -> {
                    if (ur == null || ur.getRole() == null) {
                        return false;
                    }
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null && FORBIDDEN_ROLES.stream()
                        .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
                });
            })
            .toList();
        
        // Lấy tất cả attendance records trong ngày
        List<Attendance> allAttendances = attendanceRepo.findAll().stream()
            .filter(a -> a.getWorkDate().equals(workDate))
            .toList();
        
        // Group attendance by userId
        Map<Integer, Attendance> attendanceByUserId = allAttendances.stream()
            .collect(java.util.stream.Collectors.toMap(
                Attendance::getUserId,
                a -> a,
                (a1, a2) -> a1 // Nếu có duplicate, lấy cái đầu tiên
            ));
        
        // Tính toán cho từng department
        for (Department dept : departments) {
            DailySummaryResponse summary = new DailySummaryResponse();
            summary.setDepartmentId(dept.getId());
            summary.setDepartmentName(dept.getDepartmentName());
            
            // Lấy users trong department
            List<User> deptUsers = allUsers.stream()
                .filter(u -> u.getDepartment() != null && u.getDepartment().getId().equals(dept.getId()))
                .toList();
            
            int totalEmployees = deptUsers.size();
            summary.setTotalEmployees(totalEmployees);
            
            // Giới tính (tạm thời để 0 vì User không có field gender)
            summary.setMale(0);
            summary.setFemale(0);
            
            if (totalEmployees == 0) {
                // Department không có nhân viên
                summary.setPresent(0);
                summary.setPresentPercent(BigDecimal.ZERO);
                summary.setLate(0);
                summary.setLatePercent(BigDecimal.ZERO);
                summary.setAbsent(0);
                summary.setAbsentPercent(BigDecimal.ZERO);
                summary.setLeave(0);
                summary.setLeavePercent(BigDecimal.ZERO);
                summary.setOffday(0);
                summary.setOffdayPercent(BigDecimal.ZERO);
                summaries.add(summary);
                continue;
            }
            
            // Tính toán attendance status
            int present = 0;  // ON_TIME
            int late = 0;
            int absent = 0;
            int leave = 0;    // Nghỉ phép (cần thêm logic)
            int offday = 0;   // Không có schedule
            
            for (User user : deptUsers) {
                Attendance attendance = attendanceByUserId.get(user.getId());
                
                if (attendance == null) {
                    // Không có attendance record → có thể là offday hoặc absent
                    // Cần check xem có schedule không
                    // Tạm thời coi là offday
                    offday++;
                } else {
                    String status = attendance.getAttendanceStatus();
                    if (status == null) {
                        offday++;
                    } else if ("ON_TIME".equals(status)) {
                        present++;
                    } else if ("LATE".equals(status)) {
                        late++;
                    } else if ("ABSENT".equals(status)) {
                        absent++;
                    } else if ("APPROVED_ABSENCE".equals(status)) {
                        leave++;
                    } else {
                        offday++;
                    }
                }
            }
            
            summary.setPresent(present);
            summary.setPresentPercent(calculatePercent(present, totalEmployees));
            
            summary.setLate(late);
            summary.setLatePercent(calculatePercent(late, totalEmployees));
            
            summary.setAbsent(absent);
            summary.setAbsentPercent(calculatePercent(absent, totalEmployees));
            
            summary.setLeave(leave);
            summary.setLeavePercent(calculatePercent(leave, totalEmployees));
            
            summary.setOffday(offday);
            summary.setOffdayPercent(calculatePercent(offday, totalEmployees));
            
            summaries.add(summary);
        }
        
        return summaries;
    }
    
    @Override
    public Page<DailyAttendanceListItemResponse> getDailyAttendanceList(
            LocalDate workDate, Integer departmentId, Integer clinicId, int page, int size) {
        log.info("Getting daily attendance list for date: {}, departmentId: {}, clinicId: {}", 
            workDate, departmentId, clinicId);
        
        // Lấy tất cả users active (không phải ADMIN, PATIENT)
        List<User> allUsers = userRepo.findAll().stream()
            .filter(u -> u.getIsActive() != null && u.getIsActive())
            .filter(u -> {
                // Filter by department
                if (departmentId != null) {
                    if (u.getDepartment() == null || !u.getDepartment().getId().equals(departmentId)) {
                        return false;
                    }
                }
                
                // Filter out ADMIN and PATIENT roles
                List<sunshine_dental_care.entities.UserRole> roles = userRoleRepo.findActiveByUserId(u.getId());
                if (roles == null || roles.isEmpty()) {
                    return false;
                }
                return roles.stream().noneMatch(ur -> {
                    if (ur == null || ur.getRole() == null) {
                        return false;
                    }
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null && FORBIDDEN_ROLES.stream()
                        .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
                });
            })
            .toList();
        
        // Lấy attendance records
        List<Attendance> attendances;
        if (clinicId != null) {
            attendances = attendanceRepo.findByClinicIdAndWorkDate(clinicId, workDate);
        } else {
            attendances = attendanceRepo.findAll().stream()
                .filter(a -> a.getWorkDate().equals(workDate))
                .toList();
        }
        
        // Group attendance by userId
        Map<Integer, Attendance> attendanceByUserId = attendances.stream()
            .collect(java.util.stream.Collectors.toMap(
                Attendance::getUserId,
                a -> a,
                (a1, a2) -> a1
            ));
        
        List<DailyAttendanceListItemResponse> items = new java.util.ArrayList<>();
        
        for (User user : allUsers) {
            DailyAttendanceListItemResponse item = new DailyAttendanceListItemResponse();
            item.setId(null);
            item.setUserId(user.getId());
            item.setEmployeeName(user.getFullName());
            item.setAvatarUrl(user.getAvatarUrl());
            
            // Lấy role name (job title)
            List<sunshine_dental_care.entities.UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());
            if (roles != null && !roles.isEmpty()) {
                sunshine_dental_care.entities.UserRole firstRole = roles.get(0);
                if (firstRole.getRole() != null) {
                    item.setJobTitle(firstRole.getRole().getRoleName());
                }
            }
            
            Attendance attendance = attendanceByUserId.get(user.getId());
            
            if (attendance == null) {
                // Không có attendance → Absent hoặc Offday
                item.setStatus("Absent");
                item.setStatusColor("red");
                item.setCheckInTime(null);
                item.setCheckOutTime(null);
                item.setRemarks("Fixed Attendance");
            } else {
                item.setId(attendance.getId());
                item.setCheckInTime(attendance.getCheckInTime());
                item.setCheckOutTime(attendance.getCheckOutTime());
                
                // Xác định status
                String status = attendance.getAttendanceStatus();
                if (status == null) {
                    item.setStatus("Offday");
                    item.setStatusColor("gray");
                } else if ("ON_TIME".equals(status)) {
                    item.setStatus("Present");
                    item.setStatusColor("green");
                } else if ("LATE".equals(status)) {
                    item.setStatus("Late");
                    item.setStatusColor("orange");
                } else if ("ABSENT".equals(status)) {
                    item.setStatus("Absent");
                    item.setStatusColor("red");
                } else if ("APPROVED_ABSENCE".equals(status)) {
                    item.setStatus("Approved Leave");
                    item.setStatusColor("blue");
                } else {
                    item.setStatus("Unknown");
                    item.setStatusColor("gray");
                }
                
                // Check overtime
                if (attendance.getIsOvertime() != null && attendance.getIsOvertime()) {
                    item.setStatus("Overtime");
                    item.setStatusColor("blue");
                }
                
                // Tính worked hours
                if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                    long minutes = java.time.Duration.between(
                        attendance.getCheckInTime(), 
                        attendance.getCheckOutTime()
                    ).toMinutes();
                    long hours = minutes / 60;
                    long remainingMinutes = minutes % 60;
                    item.setWorkedHours(hours);
                    item.setWorkedMinutes(remainingMinutes);
                    item.setWorkedDisplay(String.format("%d hr %02d min", hours, remainingMinutes));
                } else {
                    item.setWorkedHours(0L);
                    item.setWorkedMinutes(0L);
                    item.setWorkedDisplay("0 hr 00 min");
                }
                
                // Lấy shift từ DoctorSchedule
                List<sunshine_dental_care.entities.DoctorSchedule> schedules = doctorScheduleRepo
                    .findByUserIdAndClinicIdAndWorkDate(user.getId(), attendance.getClinicId(), workDate);
                
                if (!schedules.isEmpty()) {
                    sunshine_dental_care.entities.DoctorSchedule schedule = schedules.get(0);
                    item.setShiftStartTime(schedule.getStartTime());
                    item.setShiftEndTime(schedule.getEndTime());
                    item.setShiftDisplay(formatTime(schedule.getStartTime()) + " - " + formatTime(schedule.getEndTime()));
                    
                    long shiftHours = java.time.Duration.between(schedule.getStartTime(), schedule.getEndTime()).toHours();
                    item.setShiftHours(shiftHours + " hr Shift: A");
                } else {
                    // Không có schedule → dùng default
                    LocalTime defaultStart = getDefaultStartTime();
                    LocalTime defaultEnd = defaultStart.plusHours(9); // 9 hours shift
                    item.setShiftStartTime(defaultStart);
                    item.setShiftEndTime(defaultEnd);
                    item.setShiftDisplay(formatTime(defaultStart) + " - " + formatTime(defaultEnd));
                    item.setShiftHours("9 hr Shift: A");
                }
                
                item.setRemarks(attendance.getNote() != null ? attendance.getNote() : "Fixed Attendance");
            }
            
            items.add(item);
        }
        
        // Sort by employee name
        items.sort((a, b) -> a.getEmployeeName().compareToIgnoreCase(b.getEmployeeName()));
        
        // Apply pagination
        int start = page * size;
        int end = Math.min(start + size, items.size());
        List<DailyAttendanceListItemResponse> pagedItems = start < items.size() 
            ? items.subList(start, end) 
            : new java.util.ArrayList<>();
        
        return new org.springframework.data.domain.PageImpl<>(
            pagedItems, 
            PageRequest.of(page, size), 
            items.size()
        );
    }
    
    /**
     * Tính phần trăm
     */
    private BigDecimal calculatePercent(int value, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value)
            .divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Format LocalTime thành "9.00am" hoặc "3.00pm"
     */
    private String formatTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour < 12 ? "am" : "pm";
        int displayHour = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
        return String.format("%d.%02d%s", displayHour, minute, period);
    }
    
    @Override
    public List<MonthlySummaryResponse> getMonthlySummary(Integer year, Integer month) {
        log.info("Getting monthly summary for year: {}, month: {}", year, month);
        
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (month == null) {
            month = LocalDate.now().getMonthValue();
        }
        
        // Tính ngày đầu và cuối tháng
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        // Đếm số ngày làm việc (trừ thứ 7, chủ nhật - có thể customize)
        int workingDays = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            java.time.DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        
        // Lấy tất cả departments
        List<sunshine_dental_care.entities.Department> departments = departmentRepo.findAll();
        List<MonthlySummaryResponse> summaries = new java.util.ArrayList<>();
        
        // Lấy tất cả users active
        List<sunshine_dental_care.entities.User> allUsers = userRepo.findAll().stream()
            .filter(u -> u.getIsActive() != null && u.getIsActive())
            .filter(u -> {
                List<sunshine_dental_care.entities.UserRole> roles = userRoleRepo.findActiveByUserId(u.getId());
                if (roles == null || roles.isEmpty()) {
                    return false;
                }
                return roles.stream().noneMatch(ur -> {
                    if (ur == null || ur.getRole() == null) {
                        return false;
                    }
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null && FORBIDDEN_ROLES.stream()
                        .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
                });
            })
            .toList();
        
        // Lấy tất cả attendance trong tháng
        List<sunshine_dental_care.entities.Attendance> allAttendances = attendanceRepo.findAll().stream()
            .filter(a -> {
                LocalDate workDate = a.getWorkDate();
                return workDate != null && !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
            })
            .toList();
        
        // Group attendance by userId
        Map<Integer, List<sunshine_dental_care.entities.Attendance>> attendanceByUserId = allAttendances.stream()
            .collect(java.util.stream.Collectors.groupingBy(sunshine_dental_care.entities.Attendance::getUserId));
        
        // Tính toán cho từng department
        for (sunshine_dental_care.entities.Department dept : departments) {
            MonthlySummaryResponse summary = new MonthlySummaryResponse();
            summary.setDepartmentId(dept.getId());
            summary.setDepartmentName(dept.getDepartmentName());
            summary.setWorkingDays(workingDays);
            
            // Lấy users trong department
            List<sunshine_dental_care.entities.User> deptUsers = allUsers.stream()
                .filter(u -> u.getDepartment() != null && u.getDepartment().getId().equals(dept.getId()))
                .toList();
            
            int totalEmployees = deptUsers.size();
            summary.setTotalEmployees(totalEmployees);
            
            if (totalEmployees == 0) {
                summary.setPresent(0);
                summary.setLate(0);
                summary.setAbsent(0);
                summary.setLeave(0);
                summary.setOffday(0);
                summary.setTotalAttendance(0);
                summaries.add(summary);
                continue;
            }
            
            // Tính toán attendance status
            int present = 0;
            int late = 0;
            int absent = 0;
            int leave = 0;
            int offday = 0;
            int totalAttendance = 0;
            
            for (sunshine_dental_care.entities.User user : deptUsers) {
                List<sunshine_dental_care.entities.Attendance> userAttendances = attendanceByUserId.getOrDefault(user.getId(), new java.util.ArrayList<>());
                totalAttendance += userAttendances.size();
                
                for (sunshine_dental_care.entities.Attendance attendance : userAttendances) {
                    String status = attendance.getAttendanceStatus();
                    if (status == null) {
                        offday++;
                    } else if ("ON_TIME".equals(status)) {
                        present++;
                    } else if ("LATE".equals(status)) {
                        late++;
                    } else if ("ABSENT".equals(status)) {
                        absent++;
                    } else if ("APPROVED_ABSENCE".equals(status)) {
                        leave++;
                    } else {
                        offday++;
                    }
                }
            }
            
            summary.setPresent(present);
            summary.setLate(late);
            summary.setAbsent(absent);
            summary.setLeave(leave);
            summary.setOffday(offday);
            summary.setTotalAttendance(totalAttendance);
            
            summaries.add(summary);
        }
        
        return summaries;
    }
    
    @Override
    public Page<MonthlyAttendanceListItemResponse> getMonthlyAttendanceList(
            Integer year, Integer month, Integer departmentId, Integer clinicId, int page, int size) {
        log.info("Getting monthly attendance list for year: {}, month: {}, departmentId: {}, clinicId: {}", 
            year, month, departmentId, clinicId);
        
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (month == null) {
            month = LocalDate.now().getMonthValue();
        }
        
        // Tính ngày đầu và cuối tháng
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        // Lấy tất cả users active (không phải ADMIN, PATIENT)
        List<sunshine_dental_care.entities.User> allUsers = userRepo.findAll().stream()
            .filter(u -> u.getIsActive() != null && u.getIsActive())
            .filter(u -> {
                // Filter by department
                if (departmentId != null) {
                    if (u.getDepartment() == null || !u.getDepartment().getId().equals(departmentId)) {
                        return false;
                    }
                }
                
                // Filter out ADMIN and PATIENT roles
                List<sunshine_dental_care.entities.UserRole> roles = userRoleRepo.findActiveByUserId(u.getId());
                if (roles == null || roles.isEmpty()) {
                    return false;
                }
                return roles.stream().noneMatch(ur -> {
                    if (ur == null || ur.getRole() == null) {
                        return false;
                    }
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null && FORBIDDEN_ROLES.stream()
                        .anyMatch(forbidden -> roleName.equalsIgnoreCase(forbidden));
                });
            })
            .toList();
        
        // Lấy attendance records trong tháng
        List<sunshine_dental_care.entities.Attendance> attendances;
        if (clinicId != null) {
            attendances = attendanceRepo.findAll().stream()
                .filter(a -> {
                    LocalDate workDate = a.getWorkDate();
                    return a.getClinicId() != null && a.getClinicId().equals(clinicId)
                        && workDate != null && !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
                })
                .toList();
        } else {
            attendances = attendanceRepo.findAll().stream()
                .filter(a -> {
                    LocalDate workDate = a.getWorkDate();
                    return workDate != null && !workDate.isBefore(startDate) && !workDate.isAfter(endDate);
                })
                .toList();
        }
        
        // Group attendance by userId
        Map<Integer, List<sunshine_dental_care.entities.Attendance>> attendanceByUserId = attendances.stream()
            .collect(java.util.stream.Collectors.groupingBy(sunshine_dental_care.entities.Attendance::getUserId));
        
        List<MonthlyAttendanceListItemResponse> items = new java.util.ArrayList<>();
        
        for (sunshine_dental_care.entities.User user : allUsers) {
            MonthlyAttendanceListItemResponse item = new MonthlyAttendanceListItemResponse();
            item.setUserId(user.getId());
            item.setEmployeeName(user.getFullName());
            item.setAvatarUrl(user.getAvatarUrl());
            
            // Lấy role name (job title)
            List<sunshine_dental_care.entities.UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());
            if (roles != null && !roles.isEmpty()) {
                sunshine_dental_care.entities.UserRole firstRole = roles.get(0);
                if (firstRole.getRole() != null) {
                    item.setJobTitle(firstRole.getRole().getRoleName());
                }
            }
            
            List<sunshine_dental_care.entities.Attendance> userAttendances = attendanceByUserId.getOrDefault(user.getId(), new java.util.ArrayList<>());
            
            // Tính số ngày làm việc (có schedule)
            int workingDays = 0;
            int presentDays = 0;
            int lateDays = 0;
            int absentDays = 0;
            int leaveDays = 0;
            int offDays = 0;
            
            // Tính tổng thời gian làm việc
            long totalMinutes = 0;
            
            for (sunshine_dental_care.entities.Attendance attendance : userAttendances) {
                workingDays++;
                String status = attendance.getAttendanceStatus();
                if (status == null) {
                    offDays++;
                } else if ("ON_TIME".equals(status)) {
                    presentDays++;
                } else if ("LATE".equals(status)) {
                    lateDays++;
                } else if ("ABSENT".equals(status)) {
                    absentDays++;
                } else {
                    offDays++;
                }
                
                // Tính thời gian làm việc
                if (attendance.getCheckInTime() != null && attendance.getCheckOutTime() != null) {
                    long minutes = java.time.Duration.between(
                        attendance.getCheckInTime(), 
                        attendance.getCheckOutTime()
                    ).toMinutes();
                    totalMinutes += minutes;
                }
            }
            
            item.setWorkingDays(workingDays);
            item.setPresentDays(presentDays);
            item.setLateDays(lateDays);
            item.setAbsentDays(absentDays);
            item.setLeaveDays(leaveDays);
            item.setOffDays(offDays);
            
            long totalHours = totalMinutes / 60;
            long remainingMinutes = totalMinutes % 60;
            item.setTotalWorkedHours(totalHours);
            item.setTotalWorkedMinutes(remainingMinutes);
            item.setTotalWorkedDisplay(String.format("%d hr %02d min", totalHours, remainingMinutes));
            
            items.add(item);
        }
        
        // Sort by employee name
        items.sort((a, b) -> a.getEmployeeName().compareToIgnoreCase(b.getEmployeeName()));
        
        // Apply pagination
        int start = page * size;
        int end = Math.min(start + size, items.size());
        List<MonthlyAttendanceListItemResponse> pagedItems = start < items.size() 
            ? items.subList(start, end) 
            : new java.util.ArrayList<>();
        
        return new org.springframework.data.domain.PageImpl<>(
            pagedItems, 
            PageRequest.of(page, size), 
            items.size()
        );
    }

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

    private AttendanceResponse mapToResponseWithLookups(Attendance attendance) {
        User user = userRepo.findById(attendance.getUserId()).orElse(null);
        Clinic clinic = clinicRepo.findById(attendance.getClinicId()).orElse(null);
        return mapToResponse(attendance, user, clinic, null, null, null);
    }
}

