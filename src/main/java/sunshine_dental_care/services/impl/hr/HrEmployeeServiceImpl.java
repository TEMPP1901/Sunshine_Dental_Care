package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.EmployeeRequest;
import sunshine_dental_care.dto.hrDTO.EmployeeResponse;
import sunshine_dental_care.dto.hrDTO.ValidateEmployee;
import sunshine_dental_care.dto.hrDTO.helper.EmployeeFilterHelper;
import sunshine_dental_care.dto.hrDTO.helper.EmployeeStatisticsHelper;
import sunshine_dental_care.dto.hrDTO.mapper.EmployeeMapper;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.DoctorSpecialty;
import sunshine_dental_care.entities.LeaveRequest;
import sunshine_dental_care.entities.MedicalRecord;
import sunshine_dental_care.entities.Prescription;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeNotFoundException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeValidationException;
import sunshine_dental_care.repositories.NotificationRepository;
import sunshine_dental_care.repositories.UserDeviceRepo;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.doctor.MedicalRecordRepository;
import sunshine_dental_care.repositories.hr.AttendanceRepository;
import sunshine_dental_care.repositories.hr.DepartmentRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.repositories.hr.FaceProfileUpdateRequestRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.auth_service.MailService;
import sunshine_dental_care.services.interfaces.hr.HrEmployeeService;
import sunshine_dental_care.services.interfaces.system.AuditLogService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrEmployeeServiceImpl implements HrEmployeeService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserRoleRepo userRoleRepo;
    private final ClinicRepo clinicRepo;
    private final DepartmentRepo departmentRepo;
    private final RoomRepo roomRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final DoctorSpecialtyRepo doctorSpecialtyRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeMapper employeeMapper;
    private final EmployeeFilterHelper filterHelper;
    private final EmployeeStatisticsHelper statisticsHelper;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    private final MailService mailService;
    private final LeaveRequestRepo leaveRequestRepo;
    private final AttendanceRepository attendanceRepository;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final NotificationRepository notificationRepository;
    private final UserDeviceRepo userDeviceRepo;
    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepo appointmentRepo;
    private final FaceProfileUpdateRequestRepo faceProfileUpdateRequestRepo;
    private final AuditLogService auditLogService;
    @PersistenceContext
    private EntityManager entityManager;

    // Tạo nhân viên mới kèm liên kết các thông tin liên quan
    @Override
    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        log.info("Creating employee: {}", request.getFullName());
        try {
            ValidateEmployee.validateCreate(request, userRepo);
            User user = new User();
            user.setFullName(request.getFullName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());

            // Sinh username nếu không truyền vào
            String username = request.getUsername();
            if (username == null || username.trim().isEmpty()) {
                String emailPart = request.getEmail().split("@")[0];
                int suffix = 1;
                username = emailPart;
                while (userRepo.findByUsernameIgnoreCase(username).isPresent()) {
                    username = emailPart + suffix;
                    suffix++;
                }
            }
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

            // Tự động sinh mã nhân viên với format SDC_NV{number} nếu không có
            String employeeCode;
            if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
                employeeCode = request.getCode().trim();
                // Kiểm tra mã không trùng
                if (userRepo.findByCodeIgnoreCase(employeeCode).isPresent()) {
                    throw new EmployeeValidationException("Employee code already exists: " + employeeCode);
                }
            } else {
                employeeCode = generateEmployeeCode();
            }
            user.setCode(employeeCode);
            if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
                user.setAvatarUrl(request.getAvatarUrl().trim());
            }
            user.setProvider("local");
            user.setIsActive(true);
            if (request.getSpecialty() != null && !request.getSpecialty().trim().isEmpty()) {
                user.setSpecialty(request.getSpecialty().trim());
            }

            Department department = null;
            if (request.getDepartmentId() != null) {
                department = departmentRepo.findById(request.getDepartmentId())
                        .orElseThrow(() -> new EmployeeValidationException("Department not found"));
                user.setDepartment(department);
            }

            try {
                user = userRepo.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new EmployeeValidationException(ex);
            }

            // Thêm chuyên khoa cho bác sĩ
            if (request.getSpecialties() != null && !request.getSpecialties().isEmpty()) {
                for (String specialtyName : request.getSpecialties()) {
                    if (specialtyName != null && !specialtyName.trim().isEmpty()) {
                        DoctorSpecialty doctorSpecialty = new DoctorSpecialty();
                        doctorSpecialty.setDoctor(user);
                        doctorSpecialty.setSpecialtyName(specialtyName.trim());
                        doctorSpecialty.setIsActive(true);
                        doctorSpecialtyRepo.save(doctorSpecialty);
                    }
                }
                log.info("Saved {} specialties for doctor {}", request.getSpecialties().size(), user.getId());
            } else if (request.getSpecialty() != null && !request.getSpecialty().trim().isEmpty()) {
                DoctorSpecialty doctorSpecialty = new DoctorSpecialty();
                doctorSpecialty.setDoctor(user);
                doctorSpecialty.setSpecialtyName(request.getSpecialty().trim());
                doctorSpecialty.setIsActive(true);
                doctorSpecialtyRepo.save(doctorSpecialty);
                log.info("Saved single specialty for doctor {} (backward compatibility)", user.getId());
            }

            Role role = roleRepo.findById(request.getRoleId())
                    .orElseThrow(() -> new EmployeeValidationException("Role not found"));

            // RECEPTION (roleId: 4) và ACCOUNTANT (roleId: 5) phải có clinicId
            int roleId = request.getRoleId();
            if ((roleId == 4 || roleId == 5) && request.getClinicId() == null) {
                throw new EmployeeValidationException(
                    String.format("Clinic ID is required for %s (roleId: %d). " +
                        "RECEPTION and ACCOUNTANT employees must be assigned to a clinic.",
                        roleId == 4 ? "RECEPTION" : "ACCOUNTANT", roleId));
            }

            Clinic clinic = null;
            if (request.getClinicId() != null) {
                clinic = clinicRepo.findById(request.getClinicId())
                        .orElseThrow(() -> new EmployeeValidationException("Clinic not found"));
            } else if (roleId == 4 || roleId == 5) {
                // Double check: Nếu là RECEPTION hoặc ACCOUNTANT mà clinic vẫn null sau khi resolve
                throw new EmployeeValidationException(
                    String.format("Clinic not found or invalid for %s (roleId: %d). " +
                        "RECEPTION and ACCOUNTANT employees must be assigned to a valid clinic.",
                        roleId == 4 ? "RECEPTION" : "ACCOUNTANT", roleId));
            }

            if (request.getRoomId() != null) {
                roomRepo.findById(request.getRoomId())
                        .orElseThrow(() -> new EmployeeValidationException("Room not found"));
            }

            // Thêm mô tả phòng ban nếu có room
            String description = request.getDescription();
            if (request.getRoomId() != null && description == null) {
                description = "Room ID: " + request.getRoomId();
            } else if (request.getRoomId() != null && description != null) {
                description = description + " | Room ID: " + request.getRoomId();
            }

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setClinic(clinic);
            userRole.setDepartment(department);
            userRole.setAssignedBy(null);
            userRole.setAssignedDate(java.time.Instant.now());
            userRole.setIsActive(true);
            userRole.setDescription(description);

            try {
                userRoleRepo.save(userRole);
                log.info("UserRole saved successfully for user: {}", user.getId());
            } catch (Exception ex) {
                log.error("Failed to save UserRole: {}", ex.getMessage(), ex);
                throw new EmployeeValidationException("Failed to create user role: " + ex.getMessage(), ex);
            }

            UserClinicAssignment assignment = null;
            if (clinic != null) {
                assignment = new UserClinicAssignment();
                assignment.setUser(user);
                assignment.setClinic(clinic);
                assignment.setRoleAtClinic(null);
                assignment.setStartDate(LocalDate.now());
                assignment.setIsPrimary(true);

                try {
                    userClinicAssignmentRepo.save(assignment);
                    log.info("UserClinicAssignment saved successfully for user: {}", user.getId());
                } catch (Exception ex) {
                    log.error("Failed to save UserClinicAssignment: {}", ex.getMessage(), ex);
                    throw new EmployeeValidationException("Failed to create clinic assignment: " + ex.getMessage(), ex);
                }
            }

            log.info("Employee created without face profile. User must register face profile on first login.");

            EmployeeResponse response = new EmployeeResponse();
            response.setId(user.getId());
            response.setCode(user.getCode());
            response.setFullName(user.getFullName());
            response.setEmail(user.getEmail());
            response.setPhone(user.getPhone());
            response.setUsername(user.getUsername());
            response.setAvatarUrl(user.getAvatarUrl());
            response.setIsActive(user.getIsActive());
            response.setDepartment(user.getDepartment());
            response.setRole(role);
            if (clinic != null) {
                response.setClinic(clinic);
            }
            if (assignment != null) {
                response.setRoleAtClinic(assignment.getRoleAtClinic());
            }
            if (user.getCreatedAt() != null) {
                response.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            if (user.getUpdatedAt() != null) {
                response.setUpdatedAt(user.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            if (user.getLastLoginAt() != null) {
                response.setLastLoginAt(user.getLastLoginAt().atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            log.info("Employee created successfully: userId={}, email={}", user.getId(), user.getEmail());

            // Ghi audit log tạo nhân viên + phân công phòng khám nếu có
            User actor = getCurrentUserEntity();
            if (actor != null) {
                auditLogService.logAction(actor, "CREATE", "EMPLOYEE", user.getId(), null, null);
                if (assignment != null && assignment.getId() != null) {
                    auditLogService.logAction(actor, "ASSIGN", "USER_CLINIC_ASSIGNMENT", assignment.getId(),
                            null,
                            "Assigned user " + user.getId() + " to clinic " + assignment.getClinic().getId());
                }
            }

            // Gửi email chào mừng khi tạo mới tài khoản
            try {
                String roleName = role != null ? role.getRoleName() : null;
                String departmentName = department != null ? department.getDepartmentName() : null;
                String clinicName = clinic != null ? clinic.getClinicName() : null;
                mailService.sendWelcomeEmployeeEmail(user, request.getPassword(), roleName, departmentName, clinicName, "vi");
                log.info("Welcome email sent to employee: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send welcome email to employee {}: {}", user.getEmail(), e.getMessage(), e);
            }

            response.setFaceImageUrl(user.getAvatarUrl());

            faceProfileRepo.findByUserId(user.getId()).ifPresent(profile -> {
                response.setFaceEmbedding(profile.getFaceEmbedding());
            });
            return response;
        } catch (EmployeeValidationException | EmployeeNotFoundException ex) {
            log.error("Validation/Not found error: {}", ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error creating employee: {}", ex.getMessage(), ex);
            throw new EmployeeValidationException("Failed to create employee: " + ex.getMessage(), ex);
        }
    }

    // Lấy danh sách nhân viên có filter phân trang, trả về Page<EmployeeResponse> (lọc bằng code helper, mapping sang response)
    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getEmployees(String search, Integer clinicId, Integer departmentId,
                                               Integer roleId, Boolean isActive, int page, int size) {
        log.info("Getting employees with search: {}, clinicId: {}, departmentId: {}, roleId: {}, isActive: {}",
                search, clinicId, departmentId, roleId, isActive);
        try {
            if (page < 0) {
                throw new EmployeeValidationException("Page number cannot be negative");
            }
            if (size <= 0) {
                throw new EmployeeValidationException("Page size must be greater than 0");
            }
            if (size > 100) {
                throw new EmployeeValidationException("Page size cannot exceed 100");
            }

            Pageable pageable = PageRequest.of(page, size);
            List<User> allUsersList = userRepo.findAll();

            // Lọc theo filter (code helper chuẩn hóa luồng filter phân quyền/role/dept/clinic)
            java.util.stream.Stream<User> filteredStream = allUsersList.stream();
            filteredStream = filterHelper.applyMandatoryRoleFilter(filteredStream, isActive);
            filteredStream = filterHelper.applySearchFilter(filteredStream, search);
            filteredStream = filterHelper.applyDepartmentFilter(filteredStream, departmentId);
            filteredStream = filterHelper.applyIsActiveFilter(filteredStream, isActive);
            filteredStream = filterHelper.applyClinicFilter(filteredStream, clinicId);
            filteredStream = filterHelper.applyRoleFilter(filteredStream, roleId, isActive);

            List<User> filteredList = filteredStream.collect(java.util.stream.Collectors.toList());
            int totalElements = filteredList.size();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), totalElements);
            List<User> pagedContent = (start < totalElements)
                    ? filteredList.subList(start, end)
                    : java.util.Collections.emptyList();

            Page<User> users = new org.springframework.data.domain.PageImpl<>(pagedContent, pageable, totalElements);

            return employeeMapper.mapUsersToResponses(users);

        } catch (EmployeeValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error in getEmployees: {}", ex.getMessage(), ex);
            throw new EmployeeValidationException("Failed to get employees: " + ex.getMessage(), ex);
        }
    }

    // Lấy chi tiết nhân viên theo id (kèm mapping sang EmployeeResponse)
    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Integer id) {
        log.info("Getting employee by id: {}", id);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return employeeMapper.toEmployeeResponseWithDetails(user);
    }

    // Cập nhật thông tin nhân viên, update các trường cơ bản, không cập nhật profile khuôn mặt
    @Override
    @Transactional
    public EmployeeResponse updateEmployee(Integer id, EmployeeRequest request) {
        log.info("Updating employee: {}", id);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        ValidateEmployee.validateUpdate(id, request, userRepo);

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            user.setCode(request.getCode().trim());
        }
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getUsername() != null) user.setUsername(request.getUsername());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepo.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EmployeeValidationException("Department not found"));
            user.setDepartment(department);
        }

        user = userRepo.save(user);

        return employeeMapper.toEmployeeResponse(user);
    }

    // Bật/tắt trạng thái hoạt động (active/inactive) cho nhân viên đồng thời cập nhật trạng thái các UserRole liên quan
    @Override
    @Transactional
    public EmployeeResponse toggleEmployeeStatus(Integer id, Boolean isActive, String reason) {
        log.info("Toggling employee status: {} to {}", id, isActive);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        ValidateEmployee.validateToggle(isActive, reason, user.getIsActive());

        user.setIsActive(isActive);
        user = userRepo.save(user);

        // Khi unlock (isActive = true), cần lấy TẤT CẢ UserRole (kể cả inactive) để set lại active
        // Vì khi lock, tất cả UserRole đã bị set inactive, nên findActiveByUserId sẽ không trả về chúng
        List<UserRole> userRoles;
        if (Boolean.TRUE.equals(isActive)) {
            // Unlock: Lấy tất cả UserRole để set lại active
            userRoles = userRoleRepo.findByUserId(id);
        } else {
            // Lock: Chỉ cần lấy các UserRole đang active để set inactive
            userRoles = userRoleRepo.findActiveByUserId(id);
        }
        
        for (UserRole userRole : userRoles) {
            userRole.setIsActive(isActive);
        }
        userRoleRepo.saveAll(userRoles);

        log.info("Employee {} status changed to {} with reason: {}", id, isActive, reason);

        User actor = getCurrentUserEntity();
        if (actor != null) {
            String action = Boolean.TRUE.equals(isActive) ? "UNLOCK" : "LOCK";
            auditLogService.logAction(actor, action, "EMPLOYEE", user.getId(), null, reason);
        }

        return employeeMapper.toEmployeeResponse(user);
    }

    // Thống kê số lượng nhân viên (theo tổng số, trạng thái, phòng ban, vai trò, cơ sở)
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Integer clinicId, Integer departmentId) {
        log.info("Getting employee statistics for clinicId: {}, departmentId: {}", clinicId, departmentId);

        Map<String, Object> stats = new HashMap<>();

        try {
            List<User> allUsersList = userRepo.findAll();
            java.util.stream.Stream<User> filteredStream = allUsersList.stream();
            filteredStream = statisticsHelper.applyRoleFilterForStatistics(filteredStream);
            filteredStream = filterHelper.applyDepartmentFilter(filteredStream, departmentId);
            filteredStream = filterHelper.applyClinicFilter(filteredStream, clinicId);
            List<User> filteredList = filteredStream.collect(java.util.stream.Collectors.toList());

            stats = statisticsHelper.calculateStatistics(filteredList);
        } catch (Exception ex) {
            log.error("Error calculating statistics: {}", ex.getMessage(), ex);
            stats.put("totalEmployees", 0);
            stats.put("activeEmployees", 0);
            stats.put("inactiveEmployees", 0);
            stats.put("byDepartment", new HashMap<>());
            stats.put("byRole", new HashMap<>());
            stats.put("byClinic", new HashMap<>());
        }

        return stats;
    }

    // Xóa vĩnh viễn nhân viên (hard delete) CHỈ CHO PHÉP HR, chỉ thực hiện được khi đã có đơn nghỉ việc được duyệt
    @Override
    @Transactional
    public void hardDeleteEmployee(Integer id, String reason) {
        log.info("HR is hard deleting employee: {} with reason: {}", id, reason);

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new EmployeeValidationException("Reason is required for hard deleting employee");
            }
            if (reason.trim().length() < 10) {
                throw new EmployeeValidationException("Reason must be at least 10 characters");
            }

            User user = userRepo.findById(id)
                    .orElseThrow(() -> new EmployeeNotFoundException(id));

            // Phải có đơn nghỉ việc status = APPROVED (đã được HR duyệt và admin xác nhận)
            List<LeaveRequest> resignationRequests = leaveRequestRepo.findByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
                    id, "RESIGNATION", "APPROVED");
            
            boolean hasApprovedResignation = !resignationRequests.isEmpty();
            if (!hasApprovedResignation) {
                throw new EmployeeValidationException(
                    "Cannot hard delete employee: Employee must have an approved resignation request (type=RESIGNATION, status=APPROVED). " +
                    "The resignation request must be approved by both HR and Admin. " +
                    "Only HR can perform hard delete after Admin has confirmed the resignation request.");
            }

            log.info("Found approved resignation request for employee {}, proceeding with hard delete", id);

            // Không được xóa nếu nhân viên vẫn còn trong ca làm việc (chưa checkout)
            LocalDate today = LocalDate.now();
            List<sunshine_dental_care.entities.Attendance> todayAttendances = 
                attendanceRepository.findAllByUserIdAndWorkDate(id, today);
            boolean hasActiveAttendance = todayAttendances.stream()
                .anyMatch(a -> a.getCheckInTime() != null && a.getCheckOutTime() == null);
            if (hasActiveAttendance) {
                throw new EmployeeValidationException(
                    "Cannot hard delete employee: Employee is currently checked in. Please check out first.");
            }

            // Nếu là bác sĩ, phải hoàn tất tất cả lịch còn active trong tương lai mới được xóa
            List<UserRole> userRoles = userRoleRepo.findByUserId(id);
            boolean isDoctor = userRoles != null && userRoles.stream()
                .anyMatch(ur -> {
                    if (ur == null || ur.getRole() == null) return false;
                    String roleName = ur.getRole().getRoleName();
                    return roleName != null && 
                           (roleName.equalsIgnoreCase("DOCTOR") || roleName.equalsIgnoreCase("BÁC SĨ"));
                });
            
            if (isDoctor) {
                List<sunshine_dental_care.entities.DoctorSchedule> futureSchedules = 
                    doctorScheduleRepo.findByDoctorIdAndDateRange(id, today, LocalDate.of(2100, 12, 31));
                if (!futureSchedules.isEmpty()) {
                    List<sunshine_dental_care.entities.DoctorSchedule> activeFutureSchedules = 
                        futureSchedules.stream()
                            .filter(s -> s.getStatus() != null && "ACTIVE".equals(s.getStatus()))
                            .collect(java.util.stream.Collectors.toList());
                    if (!activeFutureSchedules.isEmpty()) {
                        LocalDate earliestScheduleDate = activeFutureSchedules.stream()
                            .map(s -> s.getWorkDate())
                            .min(LocalDate::compareTo)
                            .orElse(today);
                        String formattedDate = earliestScheduleDate.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        throw new EmployeeValidationException(
                            String.format("Không thể xóa bác sĩ: Bác sĩ còn %d lịch làm việc đang hoạt động trong tương lai. " +
                                "Vui lòng hoàn tất hoặc hủy tất cả lịch làm việc trước khi xóa. " +
                                "Ngày sớm nhất có lịch: %s. " +
                                "Số lượng lịch cần xử lý: %d.",
                                activeFutureSchedules.size(), formattedDate, activeFutureSchedules.size()));
                    }
                }
            }

            log.info("Deleting related data for employee {}", id);

            // 1. Xóa tất cả UserRole liên quan
            if (userRoles != null && !userRoles.isEmpty()) {
                // Set lại assignedBy về null cho các UserRole tham chiếu đến nhân viên này
                List<UserRole> rolesWithAssignedBy = userRoleRepo.findAll().stream()
                    .filter(ur -> ur.getAssignedBy() != null && ur.getAssignedBy().getId().equals(id))
                    .collect(java.util.stream.Collectors.toList());
                if (!rolesWithAssignedBy.isEmpty()) {
                    for (UserRole ur : rolesWithAssignedBy) {
                        ur.setAssignedBy(null);
                    }
                    userRoleRepo.saveAll(rolesWithAssignedBy);
                    log.info("Set assignedBy = null for {} UserRoles that were assigned by employee {}", 
                        rolesWithAssignedBy.size(), id);
                }
                
                userRoleRepo.deleteAll(userRoles);
                log.info("Deleted {} UserRoles for employee {}", userRoles.size(), id);
            }

            // 2. Xóa tất cả Attendance (query tất cả không giới hạn date range để đảm bảo không bỏ sót)
            // Sử dụng query trực tiếp để lấy tất cả attendance của user
            List<sunshine_dental_care.entities.Attendance> allAttendances = entityManager.createQuery(
                "SELECT a FROM Attendance a WHERE a.userId = :userId", 
                sunshine_dental_care.entities.Attendance.class)
                .setParameter("userId", id)
                .getResultList();
            if (!allAttendances.isEmpty()) {
                attendanceRepository.deleteAll(allAttendances);
                log.info("Deleted {} Attendance records for employee {}", allAttendances.size(), id);
            }

            // 3. Xóa lịch làm việc của bác sĩ (query tất cả không giới hạn date range để đảm bảo không bỏ sót)
            List<sunshine_dental_care.entities.DoctorSchedule> schedules = entityManager.createQuery(
                "SELECT ds FROM DoctorSchedule ds WHERE ds.doctor.id = :doctorId", 
                sunshine_dental_care.entities.DoctorSchedule.class)
                .setParameter("doctorId", id)
                .getResultList();
            if (!schedules.isEmpty()) {
                doctorScheduleRepo.deleteAll(schedules);
                log.info("Deleted {} DoctorSchedules for employee {}", schedules.size(), id);
            }

            // 4. Xóa các đơn nghỉ phép/đơn nghỉ việc
            List<LeaveRequest> leaveRequests = leaveRequestRepo.findByUserIdOrderByCreatedAtDesc(id);
            if (!leaveRequests.isEmpty()) {
                leaveRequestRepo.deleteAll(leaveRequests);
                log.info("Deleted {} LeaveRequests for employee {}", leaveRequests.size(), id);
            }

            // 5. Xóa EmployeeFaceProfile
            faceProfileRepo.findByUserId(id).ifPresent(profile -> {
                faceProfileRepo.delete(profile);
                log.info("Deleted EmployeeFaceProfile for employee {}", id);
            });

            // 6. Xóa phân công phòng khám
            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(id);
            if (!assignments.isEmpty()) {
                userClinicAssignmentRepo.deleteAll(assignments);
                log.info("Deleted {} UserClinicAssignments for employee {}", assignments.size(), id);
            }

            // 7. Xóa log/thông báo liên quan đến user
            org.springframework.data.domain.Page<sunshine_dental_care.entities.Log> notificationPage = 
                notificationRepository.findByUserIdOrderByCreatedAtDesc(id, 
                    org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
            List<sunshine_dental_care.entities.Log> notifications = notificationPage.getContent();
            if (!notifications.isEmpty()) {
                notificationRepository.deleteAll(notifications);
                log.info("Deleted {} Notifications for employee {}", notifications.size(), id);
            }

            // 8. Xóa thiết bị đăng nhập của user
            long deviceCount = userDeviceRepo.findByUserId(id).size();
            if (deviceCount > 0) {
                userDeviceRepo.deleteAll(userDeviceRepo.findByUserId(id));
                log.info("Deleted {} UserDevices for employee {}", deviceCount, id);
            }

            // 9. Xóa hồ sơ bệnh án do bác sĩ tạo
            List<MedicalRecord> medicalRecords = medicalRecordRepository.findAll().stream()
                .filter(mr -> mr.getDoctor() != null && mr.getDoctor().getId().equals(id))
                .collect(java.util.stream.Collectors.toList());
            if (!medicalRecords.isEmpty()) {
                medicalRecordRepository.deleteAll(medicalRecords);
                log.info("Deleted {} MedicalRecords for employee {}", medicalRecords.size(), id);
            }

            // 10. Xóa các cuộc hẹn liên quan (doctorId, createdBy)
            List<Appointment> appointments = appointmentRepo.findAll().stream()
                .filter(a -> (a.getDoctor() != null && a.getDoctor().getId().equals(id)) ||
                            (a.getCreatedBy() != null && a.getCreatedBy().getId().equals(id)))
                .collect(java.util.stream.Collectors.toList());
            if (!appointments.isEmpty()) {
                appointmentRepo.deleteAll(appointments);
                log.info("Deleted {} Appointments for employee {}", appointments.size(), id);
            }

            // 11. Xóa đơn thuốc (Prescription) - dùng EntityManager vì không có repository riêng
            List<Prescription> prescriptions = entityManager.createQuery(
                "SELECT p FROM Prescription p WHERE p.doctor.id = :doctorId", Prescription.class)
                .setParameter("doctorId", id)
                .getResultList();
            if (!prescriptions.isEmpty()) {
                for (Prescription p : prescriptions) {
                    entityManager.remove(p);
                }
                entityManager.flush();
                log.info("Deleted {} Prescriptions for employee {}", prescriptions.size(), id);
            }

            // 12. Xóa yêu cầu cập nhật profile khuôn mặt
            faceProfileUpdateRequestRepo.findByUserId(id).ifPresent(request -> {
                faceProfileUpdateRequestRepo.delete(request);
                log.info("Deleted FaceProfileUpdateRequest for employee {}", id);
            });

            // 13. Xóa tất cả DoctorSpecialty
            List<DoctorSpecialty> specialties = doctorSpecialtyRepo.findByDoctorId(id);
            if (!specialties.isEmpty()) {
                doctorSpecialtyRepo.deleteAll(specialties);
                log.info("Deleted {} DoctorSpecialties for employee {}", specialties.size(), id);
            }

            // 14. Gửi email thông báo cho nhân sự trước khi xóa (gửi trước để đảm bảo user còn tồn tại)
            try {
                mailService.sendEmployeeDeletionEmail(user, reason, "vi");
                log.info("Hard deletion notification email sent for employee: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send hard deletion notification email for employee {}: {}", 
                    user.getEmail(), e.getMessage(), e);
                // Không fail transaction nếu email lỗi
            }

            // 15. Cuối cùng xóa entity User
            userRepo.delete(user);
            log.info("Hard deleted employee {} successfully", id);

            User actor = getCurrentUserEntity();
            if (actor != null) {
                auditLogService.logAction(actor, "DELETE", "EMPLOYEE", id, null, reason);
            }

        } catch (EmployeeNotFoundException | EmployeeValidationException ex) {
            log.error("Error hard deleting employee {}: {}", id, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error hard deleting employee {}: {}", id, ex.getMessage(), ex);
            throw new EmployeeValidationException("Failed to hard delete employee: " + ex.getMessage(), ex);
        }
    }

    // Lấy danh sách tất cả bác sĩ đang hoạt động
    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAllDoctors() {
        log.info("Getting all active doctors");
        try {
            var allUserRoles = userRoleRepo.findAll();
            var doctors = allUserRoles.stream()
                    .filter(ur -> Boolean.TRUE.equals(ur.getIsActive())
                            && ur.getRole() != null
                            && "DOCTOR".equalsIgnoreCase(ur.getRole().getRoleName()))
                    .map(ur -> ur.getUser())
                    .filter(u -> u != null && Boolean.TRUE.equals(u.getIsActive()))
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            return doctors.stream()
                    .map(user -> employeeMapper.toEmployeeResponse(user))
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception ex) {
            log.error("Error getting all doctors: {}", ex.getMessage(), ex);
            throw new EmployeeValidationException("Failed to get doctors: " + ex.getMessage(), ex);
        }
    }

    // Tự động sinh mã nhân viên với format SDC_NV000xxx (6 chữ số với leading zeros)
    private String generateEmployeeCode() {
        // Tìm tất cả mã nhân viên có format SDC_NV*
        List<User> allUsers = userRepo.findAll();
        int maxNumber = 0;
        
        for (User user : allUsers) {
            String code = user.getCode();
            if (code != null && code.toUpperCase().startsWith("SDC_NV")) {
                try {
                    // Lấy số sau "SDC_NV" (bỏ qua các số 0 đệm phía trước)
                    String numberPart = code.substring(6); // "SDC_NV" có 6 ký tự
                    // Loại bỏ leading zeros để parse số
                    String numberPartTrimmed = numberPart.replaceFirst("^0+", "");
                    if (numberPartTrimmed.isEmpty()) {
                        numberPartTrimmed = "0"; // Nếu toàn bộ là số 0
                    }
                    int number = Integer.parseInt(numberPartTrimmed);
                    if (number > maxNumber) {
                        maxNumber = number;
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua các mã không đúng format
                    log.debug("Skipping invalid employee code format: {}", code);
                }
            }
        }
        
        // Tăng số lên 1
        int nextNumber = maxNumber + 1;
        
        // Format với 6 chữ số, có leading zeros: SDC_NV000001, SDC_NV000002, ...
        String newCode = String.format("SDC_NV%06d", nextNumber);
        
        // Double check: Đảm bảo mã không trùng (trường hợp hiếm)
        int retryCount = 0;
        while (userRepo.findByCodeIgnoreCase(newCode).isPresent() && retryCount < 100) {
            nextNumber++;
            newCode = String.format("SDC_NV%06d", nextNumber);
            retryCount++;
        }
        
        if (retryCount >= 100) {
            throw new EmployeeValidationException("Failed to generate unique employee code after 100 attempts");
        }
        
        log.info("Generated employee code: {}", newCode);
        return newCode;
    }

    // Preview mã nhân viên sẽ được sinh (để hiển thị trên form trước khi tạo)
    @Override
    @Transactional(readOnly = true)
    public String previewEmployeeCode() {
        return generateEmployeeCode();
    }

    // Lấy user hiện tại từ SecurityContext để ghi audit log (nếu không có thì bỏ qua log)
    private User getCurrentUserEntity() {
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
}
