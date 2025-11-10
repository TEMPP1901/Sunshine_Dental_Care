package sunshine_dental_care.services.impl.hr;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.EmployeeRequest;
import sunshine_dental_care.dto.hrDTO.EmployeeResponse;
import sunshine_dental_care.dto.hrDTO.ValidateEmployee;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Department;
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
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.services.interfaces.hr.HrEmployeeService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrEmployeeServiceImpl implements HrEmployeeService {

    // Tái sử dụng repositories từ Auth
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserRoleRepo userRoleRepo;
    private final ClinicRepo clinicRepo;
    
    // Repositories mới cho HR
    private final DepartmentRepo departmentRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    
    // Services khác
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request) {
        log.info("Creating employee: {}", request.getFullName());
        
        ValidateEmployee.validateCreate(request, userRepo);
        
        // Tạo User
        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCode(request.getCode());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setProvider("local");
        user.setIsActive(true);
        
        // Set department
        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepo.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EmployeeValidationException("Department not found"));
            user.setDepartment(department);
        }
        
        try {
            user = userRepo.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            Throwable root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
            throw new EmployeeValidationException("Failed to create user: " + root.getMessage(), ex);
        }
        
        // Tạo UserRole
        Role role = roleRepo.findById(request.getRoleId())
                .orElseThrow(() -> new EmployeeValidationException("Role not found"));
        
        Clinic clinic = clinicRepo.findById(request.getClinicId())
                .orElseThrow(() -> new EmployeeValidationException("Clinic not found"));
        
        // Use department from above (already validated)
        
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setClinic(clinic);
        userRole.setDepartment(department);
        userRole.setAssignedBy(null); // TODO: Get current user from security context
        userRole.setAssignedDate(java.time.Instant.now());
        userRole.setIsActive(true);
        userRole.setDescription(request.getDescription());
        
        userRoleRepo.save(userRole);
        
        // Tạo UserClinicAssignment
        UserClinicAssignment assignment = new UserClinicAssignment();
        assignment.setUser(user);
        assignment.setClinic(clinic);
        assignment.setRoleAtClinic(request.getRoleAtClinic());
        assignment.setStartDate(LocalDate.now());
        assignment.setIsPrimary(true);
        
        userClinicAssignmentRepo.save(assignment);
        
        // Trả về response
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
        response.setClinic(clinic);
        response.setRoleAtClinic(assignment.getRoleAtClinic());
        
        // Convert timestamps
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getUpdatedAt() != null) {
            response.setUpdatedAt(user.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getLastLoginAt() != null) {
            response.setLastLoginAt(user.getLastLoginAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getEmployees(String search, Integer clinicId, Integer departmentId, 
                                              Integer roleId, Boolean isActive, int page, int size) {
        log.info("Getting employees with search: {}, clinicId: {}, departmentId: {}, roleId: {}, isActive: {}", 
                search, clinicId, departmentId, roleId, isActive);
        
        // Validate pagination parameters
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
        Page<User> users = userRepo.findAll(pageable);
        
        return users.map(user -> {
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
            
            // Convert timestamps
            if (user.getCreatedAt() != null) {
                response.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            if (user.getUpdatedAt() != null) {
                response.setUpdatedAt(user.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            if (user.getLastLoginAt() != null) {
                response.setLastLoginAt(user.getLastLoginAt().atZone(ZoneOffset.UTC).toLocalDateTime());
            }
            
            return response;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Integer id) {
        log.info("Getting employee by id: {}", id);
        
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        
        // Get user roles and assignments
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(id);
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(id);
        
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
        
        // Add role and clinic info if available
        if (!userRoles.isEmpty()) {
            response.setRole(userRoles.get(0).getRole());
        }
        if (!assignments.isEmpty()) {
            response.setClinic(assignments.get(0).getClinic());
            response.setRoleAtClinic(assignments.get(0).getRoleAtClinic());
        }
        
        // Convert timestamps
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getUpdatedAt() != null) {
            response.setUpdatedAt(user.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getLastLoginAt() != null) {
            response.setLastLoginAt(user.getLastLoginAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        
        return response;
    }

    @Override
    @Transactional
    public EmployeeResponse updateEmployee(Integer id, EmployeeRequest request) {
        log.info("Updating employee: {}", id);
        
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        
        ValidateEmployee.validateUpdate(id, request, userRepo);
        
        // Update basic info
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        
        // Update department
        if (request.getDepartmentId() != null) {
            Department department = departmentRepo.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EmployeeValidationException("Department not found"));
            user.setDepartment(department);
        }
        
        user = userRepo.save(user);
        
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
        
        // Convert timestamps
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getUpdatedAt() != null) {
            response.setUpdatedAt(user.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getLastLoginAt() != null) {
            response.setLastLoginAt(user.getLastLoginAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        
        return response;
    }

    @Override
    @Transactional
    public EmployeeResponse toggleEmployeeStatus(Integer id, Boolean isActive, String reason) {
        log.info("Toggling employee status: {} to {}", id, isActive);
        
        User user = userRepo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        
        ValidateEmployee.validateToggle(isActive, reason, user.getIsActive());
        
        user.setIsActive(isActive);
        user = userRepo.save(user);
        
        // Update all user roles
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(id);
        for (UserRole userRole : userRoles) {
            userRole.setIsActive(isActive);
        }
        userRoleRepo.saveAll(userRoles);
        
        log.info("Employee {} status changed to {} with reason: {}", id, isActive, reason);
        
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
        
        // Convert timestamps
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getUpdatedAt() != null) {
            response.setUpdatedAt(user.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        if (user.getLastLoginAt() != null) {
            response.setLastLoginAt(user.getLastLoginAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Integer clinicId, Integer departmentId) {
        log.info("Getting employee statistics for clinicId: {}, departmentId: {}", clinicId, departmentId);
        
        Map<String, Object> stats = new HashMap<>();
        
        // Simple stats for now
        stats.put("totalEmployees", 0);
        stats.put("activeEmployees", 0);
        stats.put("inactiveEmployees", 0);
        stats.put("byDepartment", new HashMap<>());
        stats.put("message", "Statistics endpoint working");
        
        log.info("Statistics calculated successfully: {}", stats);
        return stats;
    }

}
