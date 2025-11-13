package sunshine_dental_care.dto.hrDTO.helper;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeFilterHelper {

    private final UserRoleRepo userRoleRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;

    // Allowed role names for employee list (case-insensitive)
    private static final List<String> ALLOWED_ROLE_NAMES = List.of(
        "RECEPTIONIST", "ACCOUNTANT", "DOCTOR", "HR",
        "Receptionist", "Accountant", "Doctor", "HR",
        "lễ tân", "kế toán", "bác sĩ", "hr"
    );

    /**
     * Apply mandatory role filter: Only show employees with specific roles
     */
    public Stream<User> applyMandatoryRoleFilter(Stream<User> userStream) {
        log.debug("Applying mandatory role filter (RECEPTIONIST, ACCOUNTANT, DOCTOR, HR)");
        return userStream.filter(u -> {
            if (u == null || u.getId() == null) {
                log.debug("Filtering out null user or null user ID");
                return false;
            }
            try {
                List<sunshine_dental_care.entities.UserRole> userRoles = userRoleRepo.findActiveByUserId(u.getId());
                if (userRoles == null || userRoles.isEmpty()) {
                    log.debug("User {} has no active roles, excluding", u.getId());
                    return false;
                }

                boolean hasAllowedRole = userRoles.stream()
                    .anyMatch(ur -> {
                        if (ur == null || ur.getRole() == null) {
                            return false;
                        }
                        String roleName = ur.getRole().getRoleName();
                        if (roleName == null || roleName.trim().isEmpty()) {
                            return false;
                        }
                        return ALLOWED_ROLE_NAMES.stream()
                            .anyMatch(allowedName -> roleName.equalsIgnoreCase(allowedName));
                    });

                if (!hasAllowedRole) {
                    log.debug("User {} does not have any allowed role, excluding", u.getId());
                }
                return hasAllowedRole;
            } catch (Exception ex) {
                log.warn("Error filtering by default roles for user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }

    /**
     * Apply search filter (by name, email, username, phone)
     */
    public Stream<User> applySearchFilter(Stream<User> userStream, String search) {
        if (search == null || search.trim().isEmpty()) {
            return userStream;
        }

        String lowerSearch = search.toLowerCase();
        log.debug("Applying search filter: {}", search);
        return userStream.filter(u -> {
            return (u.getFullName() != null && u.getFullName().toLowerCase().contains(lowerSearch)) ||
                   (u.getEmail() != null && u.getEmail().toLowerCase().contains(lowerSearch)) ||
                   (u.getUsername() != null && u.getUsername().toLowerCase().contains(lowerSearch)) ||
                   (u.getPhone() != null && u.getPhone().contains(search));
        });
    }

    /**
     * Apply department filter
     */
    public Stream<User> applyDepartmentFilter(Stream<User> userStream, Integer departmentId) {
        if (departmentId == null) {
            return userStream;
        }

        log.debug("Applying departmentId filter: {}", departmentId);
        return userStream.filter(u -> {
            try {
                if (u.getDepartment() == null) {
                    log.debug("User {} has no department, excluding", u.getId());
                    return false;
                }
                Integer userDeptId = u.getDepartment().getId();
                boolean matches = userDeptId != null && userDeptId.equals(departmentId);
                if (!matches) {
                    log.debug("User {} departmentId {} does not match filter {}, excluding", 
                            u.getId(), userDeptId, departmentId);
                }
                return matches;
            } catch (Exception ex) {
                log.warn("Error filtering by departmentId for user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }

    /**
     * Apply isActive filter
     */
    public Stream<User> applyIsActiveFilter(Stream<User> userStream, Boolean isActive) {
        if (isActive == null) {
            return userStream;
        }

        log.debug("Applying isActive filter: {}", isActive);
        return userStream.filter(u -> {
            Boolean userIsActive = u.getIsActive();
            boolean matches = isActive.equals(userIsActive);
            if (!matches) {
                log.debug("User {} isActive {} does not match filter {}, excluding", 
                        u.getId(), userIsActive, isActive);
            }
            return matches;
        });
    }

    /**
     * Apply clinic filter
     */
    public Stream<User> applyClinicFilter(Stream<User> userStream, Integer clinicId) {
        if (clinicId == null) {
            return userStream;
        }

        log.debug("Applying clinicId filter: {}", clinicId);
        return userStream.filter(u -> {
            try {
                List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(u.getId());
                return assignments != null && assignments.stream()
                    .anyMatch(a -> a != null && a.getClinic() != null && 
                            a.getClinic().getId() != null && a.getClinic().getId().equals(clinicId));
            } catch (Exception ex) {
                log.warn("Error filtering by clinicId for user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }

    /**
     * Apply role filter (additional to mandatory filter)
     */
    public Stream<User> applyRoleFilter(Stream<User> userStream, Integer roleId) {
        if (roleId == null) {
            return userStream;
        }

        log.debug("Applying additional roleId filter: {}", roleId);
        return userStream.filter(u -> {
            try {
                List<sunshine_dental_care.entities.UserRole> userRoles = userRoleRepo.findActiveByUserId(u.getId());
                if (userRoles == null || userRoles.isEmpty()) {
                    log.debug("User {} has no active roles for roleId filter, excluding", u.getId());
                    return false;
                }
                boolean matchesRoleId = userRoles.stream()
                    .anyMatch(ur -> {
                        if (ur == null || ur.getRole() == null) {
                            return false;
                        }
                        Integer urRoleId = ur.getRole().getId();
                        return urRoleId != null && urRoleId.equals(roleId);
                    });
                if (!matchesRoleId) {
                    log.debug("User {} does not match roleId {}, excluding", u.getId(), roleId);
                } else {
                    log.debug("User {} matches roleId {}", u.getId(), roleId);
                }
                return matchesRoleId;
            } catch (Exception ex) {
                log.warn("Error filtering by roleId for user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }
}

