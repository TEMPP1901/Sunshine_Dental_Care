package sunshine_dental_care.services.impl.hr;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
import sunshine_dental_care.entities.DoctorSpecialty;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeNotFoundException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DepartmentRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.services.interfaces.hr.FaceRecognitionService;
import sunshine_dental_care.services.interfaces.hr.HrEmployeeService;

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
    private final FaceRecognitionService faceRecognitionService;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    // Tạo mới nhân viên (user) và các liên kết liên quan (role, phòng ban, clinic, ...)
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        log.info("Creating employee: {}", request.getFullName());
        try {
            // Kiểm tra hợp lệ đầu vào khi tạo nhân viên
            ValidateEmployee.validateCreate(request, userRepo);

            User user = new User();
            user.setFullName(request.getFullName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());

            // Xử lý logic tạo username nếu chưa được truyền lên
            String username = request.getUsername();
            if (username == null || username.trim().isEmpty()) {
                String emailPart = request.getEmail().split("@")[0];
                int suffix = 1;
                username = emailPart;
                // Đảm bảo username không bị trùng
                while (userRepo.findByUsernameIgnoreCase(username).isPresent()) {
                    username = emailPart + suffix;
                    suffix++;
                }
            }
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

            // Lưu code nếu có nhập
            if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
                user.setCode(request.getCode().trim());
            }
            // Avatar URL được upload riêng qua endpoint /avatar, không cần set ở đây
            if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
                user.setAvatarUrl(request.getAvatarUrl().trim());
            }
            user.setProvider("local");
            user.setIsActive(true);
            
            // Lưu specialty cũ vào field User.specialty (deprecated, chỉ để backward compatibility)
            // Frontend hiện tại không gửi field này, nhưng giữ lại để tương thích với các client khác
            if (request.getSpecialty() != null && !request.getSpecialty().trim().isEmpty()) {
                user.setSpecialty(request.getSpecialty().trim());
            }

            Department department = null;
            if (request.getDepartmentId() != null) {
                // Lấy phòng ban, nếu không tồn tại thì báo lỗi
                department = departmentRepo.findById(request.getDepartmentId())
                        .orElseThrow(() -> new EmployeeValidationException("Department not found"));
                user.setDepartment(department);
            }

            try {
                user = userRepo.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new EmployeeValidationException(ex);
            }

            // Lưu nhiều chuyên khoa vào bảng DoctorSpecialties
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
                // Nếu chỉ có specialty cũ (backward compatibility), lưu vào bảng mới
                DoctorSpecialty doctorSpecialty = new DoctorSpecialty();
                doctorSpecialty.setDoctor(user);
                doctorSpecialty.setSpecialtyName(request.getSpecialty().trim());
                doctorSpecialty.setIsActive(true);
                doctorSpecialtyRepo.save(doctorSpecialty);
                log.info("Saved single specialty for doctor {} (backward compatibility)", user.getId());
            }

            // Lấy role, nếu không tồn tại thì báo lỗi
            Role role = roleRepo.findById(request.getRoleId())
                    .orElseThrow(() -> new EmployeeValidationException("Role not found"));

            Clinic clinic = null;
            if (request.getClinicId() != null) {
                // Lấy clinic, nếu không tồn tại thì báo lỗi
                clinic = clinicRepo.findById(request.getClinicId())
                        .orElseThrow(() -> new EmployeeValidationException("Clinic not found"));
            }

            // Room assignment - hiện tại không được sử dụng trong frontend form
            // Giữ lại để tương thích với các client khác hoặc tương lai
            if (request.getRoomId() != null) {
                // Kiểm tra tồn tại phòng
                roomRepo.findById(request.getRoomId())
                        .orElseThrow(() -> new EmployeeValidationException("Room not found"));
            }

            // Description - hiện tại không được sử dụng trong frontend form
            // Giữ lại để tương thích với các client khác hoặc tương lai
            String description = request.getDescription();
            if (request.getRoomId() != null && description == null) {
                description = "Room ID: " + request.getRoomId();
            } else if (request.getRoomId() != null && description != null) {
                description = description + " | Room ID: " + request.getRoomId();
            }

            // Tạo đối tượng UserRole để ánh xạ role cho user
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

            // Tạo phân công user với clinic nếu có
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

        // Đồng bộ face profile từ avatarUrl (nếu có)
        String avatarUrlToSync = user.getAvatarUrl();
        log.info("Attempting to sync face profile for user {} with avatarUrl: {}", user.getId(), avatarUrlToSync);
        syncFaceProfileFromAvatar(user, avatarUrlToSync);

            // Chuẩn bị dữ liệu trả về cho client
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

            // Set faceImageUrl = avatarUrl (ảnh lưu ở User.avatarUrl)
            response.setFaceImageUrl(user.getAvatarUrl());
            
            // Lấy embedding từ EmployeeFaceProfile
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

    @Override
    @Transactional(readOnly = true)
    // Lấy danh sách nhân viên có filter và phân trang
    public Page<EmployeeResponse> getEmployees(String search, Integer clinicId, Integer departmentId,
                                               Integer roleId, Boolean isActive, int page, int size) {
        log.info("Getting employees with search: {}, clinicId: {}, departmentId: {}, roleId: {}, isActive: {}",
                search, clinicId, departmentId, roleId, isActive);
        try {
            // Kiểm tra tham số phân trang
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

            // Áp dụng các bộ lọc tìm kiếm, phòng ban, clinic, role, trạng thái...
            java.util.stream.Stream<User> filteredStream = allUsersList.stream();
            filteredStream = filterHelper.applyMandatoryRoleFilter(filteredStream);
            filteredStream = filterHelper.applySearchFilter(filteredStream, search);
            filteredStream = filterHelper.applyDepartmentFilter(filteredStream, departmentId);
            filteredStream = filterHelper.applyIsActiveFilter(filteredStream, isActive);
            filteredStream = filterHelper.applyClinicFilter(filteredStream, clinicId);
            filteredStream = filterHelper.applyRoleFilter(filteredStream, roleId);

            // Lọc và phân trang thủ công
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

    @Override
    @Transactional(readOnly = true)
    // Lấy thông tin chi tiết của 1 nhân viên theo id
    public EmployeeResponse getEmployeeById(Integer id) {
        log.info("Getting employee by id: {}", id);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return employeeMapper.toEmployeeResponseWithDetails(user);
    }

    @Override
    @Transactional
    // Cập nhật thông tin của nhân viên
    public EmployeeResponse updateEmployee(Integer id, EmployeeRequest request) {
        log.info("Updating employee: {}", id);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        // Kiểm tra hợp lệ khi cập nhật thông tin nhân viên
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

        // KHÔNG sync face profile khi HR update employee
        // Chỉ extract embedding khi HR tạo mới employee hoặc upload avatar lần đầu
        // Để tránh ghi đè embedding đã được tạo từ lần đăng ký đầu tiên
        // if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
        //     syncFaceProfileFromAvatar(user, request.getAvatarUrl());
        // }

        return employeeMapper.toEmployeeResponse(user);
    }

    @Override
    @Transactional
    // Đổi trạng thái hoạt động của nhân viên (có thể khoá/mở khoá)
    public EmployeeResponse toggleEmployeeStatus(Integer id, Boolean isActive, String reason) {
        log.info("Toggling employee status: {} to {}", id, isActive);
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        // Kiểm tra hợp lệ khi đổi trạng thái
        ValidateEmployee.validateToggle(isActive, reason, user.getIsActive());

        user.setIsActive(isActive);
        user = userRepo.save(user);

        // Đổi trạng thái UserRole tương ứng
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(id);
        for (UserRole userRole : userRoles) {
            userRole.setIsActive(isActive);
        }
        userRoleRepo.saveAll(userRoles);

        log.info("Employee {} status changed to {} with reason: {}", id, isActive, reason);

        return employeeMapper.toEmployeeResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    // Thống kê số lượng nhân viên theo nhiều tiêu chí (phòng ban, clinic,...)
    public Map<String, Object> getStatistics(Integer clinicId, Integer departmentId) {
        log.info("Getting employee statistics for clinicId: {}, departmentId: {}", clinicId, departmentId);

        Map<String, Object> stats = new HashMap<>();

        try {
            // Lấy danh sách nhân viên rồi lọc theo department/clinic
            List<User> allUsersList = userRepo.findAll();
            java.util.stream.Stream<User> filteredStream = allUsersList.stream();
            filteredStream = statisticsHelper.applyRoleFilterForStatistics(filteredStream);
            filteredStream = filterHelper.applyDepartmentFilter(filteredStream, departmentId);
            filteredStream = filterHelper.applyClinicFilter(filteredStream, clinicId);
            List<User> filteredList = filteredStream.collect(java.util.stream.Collectors.toList());

            // Tính toán thống kê tổng và theo nhóm
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

    @Override
    @Transactional
    // Xoá mềm nhân viên (set isActive = false, đảm bảo cập nhật các liên kết liên quan)
    public void deleteEmployee(Integer id, String reason) {
        log.info("Deleting employee: {} with reason: {}", id, reason);

        try {
            // Kiểm tra user tồn tại
            User user = userRepo.findById(id)
                    .orElseThrow(() -> new EmployeeNotFoundException(id));

            // Đặt isActive=false cho các UserRole liên quan nếu còn active
            List<UserRole> userRoles = userRoleRepo.findActiveByUserId(id);

            if (!userRoles.isEmpty()) {
                for (UserRole userRole : userRoles) {
                    userRole.setIsActive(false);
                }
                userRoleRepo.saveAll(userRoles);
                entityManager.flush();
            }

            // Đặt isActive=false cho user
            user.setIsActive(false);
            user.setUpdatedAt(Instant.now());
            user = userRepo.save(user);
            entityManager.flush();

            // Đảm bảo trạng thái mới được cập nhật xuống DB
            entityManager.merge(user);
            entityManager.flush();

            // Clear cache để lấy dữ liệu mới nhất
            entityManager.clear();

            // Kiểm tra lại trạng thái user đã cập nhật
            User verifyUser = userRepo.findById(id).orElse(null);
            if (verifyUser != null) {
                if (Boolean.TRUE.equals(verifyUser.getIsActive())) {
                    throw new EmployeeValidationException("Failed to delete employee: update did not persist");
                }
            } else {
                throw new EmployeeNotFoundException(id);
            }

        } catch (EmployeeNotFoundException | EmployeeValidationException ex) {
            log.error("Error deleting employee {}: {}", id, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error deleting employee {}: {}", id, ex.getMessage(), ex);
            throw new EmployeeValidationException("Failed to delete employee: " + ex.getMessage(), ex);
        }
    }

    private void syncFaceProfileFromAvatar(User user, String avatarUrl) {
        if (user == null || user.getId() == null) {
            log.debug("syncFaceProfileFromAvatar: user is null or userId is null");
            return;
        }
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            log.debug("syncFaceProfileFromAvatar: avatarUrl is null or empty for user {}", user.getId());
            return;
        }
        
        String trimmedUrl = avatarUrl.trim();
        log.info("Syncing face profile from avatar URL for user {}: {}", user.getId(), trimmedUrl);

        Path tempFile = null;
        try {
            URI uri = URI.create(trimmedUrl);
            URL url = uri.toURL();
            log.debug("Downloading avatar from URL: {}", trimmedUrl);
            tempFile = Files.createTempFile("hr-avatar-", ".img");
            try (InputStream inputStream = url.openStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Avatar downloaded to temp file: {}", tempFile);

            log.info("Extracting face embedding for user {} from avatar URL", user.getId());
            String embedding = faceRecognitionService.extractEmbeddingFromPath(tempFile.toString());
            if (embedding == null || embedding.trim().isEmpty()) {
                throw new IllegalArgumentException("Extracted embedding is empty");
            }
            
            // Validate embedding format
            String trimmedEmbedding = embedding.trim();
            if (!trimmedEmbedding.startsWith("[") || !trimmedEmbedding.endsWith("]")) {
                throw new IllegalArgumentException("Invalid embedding format: must be JSON array");
            }
            
            log.debug("Embedding extracted successfully, length: {} chars", trimmedEmbedding.length());

            EmployeeFaceProfile profile = faceProfileRepo.findByUserId(user.getId())
                    .orElse(new EmployeeFaceProfile());
            profile.setUserId(user.getId());
            // Chỉ lưu embedding, không lưu faceImageUrl (ảnh lưu ở User.avatarUrl)
            profile.setFaceEmbedding(embedding);
            faceProfileRepo.save(profile);
            log.info("Face profile synced successfully for user {}: embedding saved", user.getId());
        } catch (java.net.MalformedURLException | java.net.URISyntaxException ex) {
            log.warn("Invalid avatar URL for user {}: {}. Error: {}", user.getId(), trimmedUrl, ex.getMessage());
        } catch (java.io.IOException ex) {
            log.warn("Failed to download avatar from URL for user {}: {}. Error: {}", user.getId(), trimmedUrl, ex.getMessage());
        } catch (IllegalStateException ex) {
            log.warn("Face recognition model not available for user {}: {}", user.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to sync face profile from avatar URL for user {}: {}. Error: {}", 
                user.getId(), trimmedUrl, ex.getMessage(), ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception cleanupEx) {
                    log.debug("Failed to delete temporary avatar file {}: {}", tempFile, cleanupEx.getMessage());
                }
            }
        }
    }

}
