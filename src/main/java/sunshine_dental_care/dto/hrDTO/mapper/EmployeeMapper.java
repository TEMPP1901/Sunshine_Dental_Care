package sunshine_dental_care.dto.hrDTO.mapper;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.EmployeeResponse;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeMapper {

    private final RoleRepo roleRepo;
    private final ClinicRepo clinicRepo;
    private final UserRoleRepo userRoleRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;

    /**
     * Helper method to get Role entity directly from repository (not a proxy)
     */
    public Role getRoleFromEntity(Role role) {
        if (role == null) {
            return null;
        }
        try {
            Integer roleId = role.getId();
            if (roleId == null) {
                return null;
            }
            return roleRepo.findById(roleId).orElse(null);
        } catch (Exception ex) {
            log.warn("Error loading role from repository: {}", ex.getMessage());
            try {
                String roleName = role.getRoleName();
                return role; // Return initialized proxy
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Map a single User to EmployeeResponse
     */
    public EmployeeResponse toEmployeeResponse(User user) {
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

    /**
     * Map a Page of Users to Page of EmployeeResponse with role and clinic information
     */
    public Page<EmployeeResponse> mapUsersToResponses(Page<User> users) {
        // Collect user IDs first to batch load roles
        List<Integer> userIds = users.getContent().stream()
            .map(User::getId)
            .toList();

        // Batch load all roles for all users from repository (not proxy)
        Map<Integer, Role> userRoleMap = new HashMap<>();
        try {
            for (Integer userId : userIds) {
                try {
                    List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
                    if (!userRoles.isEmpty()) {
                        UserRole firstRole = userRoles.get(0);
                        if (firstRole != null && firstRole.getRole() != null) {
                            Role role = firstRole.getRole();
                            Role realRole = getRoleFromEntity(role);
                            if (realRole != null) {
                                userRoleMap.put(userId, realRole);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error loading role for user {}: {}", userId, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Error batch loading roles: {}", ex.getMessage(), ex);
        }

        final Map<Integer, Role> roleMap = userRoleMap;

        return users.map(user -> {
            EmployeeResponse response = toEmployeeResponse(user);

            // Set role from pre-loaded map (loaded from repository)
            Role role = roleMap.get(user.getId());
            if (role != null) {
                response.setRole(role);
            }

            // Populate department if available (also initialize if needed)
            if (user.getDepartment() != null) {
                try {
                    String deptName = user.getDepartment().getDepartmentName();
                    response.setDepartment(user.getDepartment());
                } catch (Exception ex) {
                    log.warn("Error initializing department for user {}: {}", user.getId(), ex.getMessage());
                }
            }

            return response;
        });
    }

    /**
     * Map a single User to EmployeeResponse with full details (including role and clinic)
     */
    public EmployeeResponse toEmployeeResponseWithDetails(User user) {
        EmployeeResponse response = toEmployeeResponse(user);

        // Get user roles and assignments
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(user.getId());
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(user.getId());

        // Add role info if available
        if (!userRoles.isEmpty()) {
            Role role = userRoles.get(0).getRole();
            if (role != null) {
                Role realRole = getRoleFromEntity(role);
                if (realRole != null) {
                    response.setRole(realRole);
                }
            }
        }

        // Add clinic info if available
        if (!assignments.isEmpty()) {
            UserClinicAssignment assignment = assignments.get(0);
            if (assignment != null && assignment.getClinic() != null) {
                Clinic clinicProxy = assignment.getClinic();
                Integer clinicId = clinicProxy.getId();
                if (clinicId != null) {
                    Clinic realClinic = clinicRepo.findById(clinicId).orElse(null);
                    if (realClinic != null) {
                        response.setClinic(realClinic);
                        log.debug("Loaded clinic {} for employee {}", realClinic.getClinicName(), user.getId());
                    } else {
                        log.warn("Clinic with id {} not found for employee {}", clinicId, user.getId());
                    }
                } else {
                    log.warn("Clinic proxy has null id for employee {}", user.getId());
                }
                response.setRoleAtClinic(assignment.getRoleAtClinic());
            }
        } else {
            log.debug("No clinic assignments found for employee {}", user.getId());
        }

        return response;
    }
}

