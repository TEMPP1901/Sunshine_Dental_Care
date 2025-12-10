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

    // Danh sách tên vai trò được phép hiển thị trong danh sách nhân viên (không phân biệt hoa thường)
    private static final List<String> ALLOWED_ROLE_NAMES = List.of(
        "RECEPTION", "ACCOUNTANT", "DOCTOR", "HR",
        "Reception", "Accountant", "Doctor", "HR",
        "lễ tân", "kế toán", "bác sĩ", "hr"
    );

    // Lọc bắt buộc: chỉ lấy nhân viên có các vai trò thuộc danh sách cho phép
    // Lưu ý: Khi filter nhân viên đã xóa (isActive=false), cần lấy cả UserRole inactive
    public Stream<User> applyMandatoryRoleFilter(Stream<User> userStream, Boolean isActive) {
        log.debug("Applying mandatory role filter (RECEPTION, ACCOUNTANT, DOCTOR, HR), isActive: {}", isActive);
        return userStream.filter(u -> {
            if (u == null || u.getId() == null) {
                return false;
            }
            try {
                // Nếu filter nhân viên đã xóa, lấy cả UserRole inactive
                List<sunshine_dental_care.entities.UserRole> userRoles;
                if (Boolean.FALSE.equals(isActive)) {
                    // Lấy tất cả UserRole (kể cả inactive) cho nhân viên đã xóa
                    userRoles = userRoleRepo.findByUserId(u.getId());
                } else {
                    // Lấy chỉ UserRole active cho nhân viên đang hoạt động
                    userRoles = userRoleRepo.findActiveByUserId(u.getId());
                }
                
                if (userRoles == null || userRoles.isEmpty()) {
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
                return hasAllowedRole;
            } catch (Exception ex) {
                log.warn("Lỗi khi filter theo vai trò mặc định cho user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }

    // Lọc tìm kiếm nhân viên theo tên, email, username, số điện thoại
    public Stream<User> applySearchFilter(Stream<User> userStream, String search) {
        if (search == null || search.trim().isEmpty()) {
            return userStream;
        }

        String lowerSearch = search.toLowerCase();
        return userStream.filter(u -> {
            return (u.getFullName() != null && u.getFullName().toLowerCase().contains(lowerSearch)) ||
                   (u.getEmail() != null && u.getEmail().toLowerCase().contains(lowerSearch)) ||
                   (u.getUsername() != null && u.getUsername().toLowerCase().contains(lowerSearch)) ||
                   (u.getPhone() != null && u.getPhone().contains(search));
        });
    }

    // Lọc nhân viên theo phòng ban
    public Stream<User> applyDepartmentFilter(Stream<User> userStream, Integer departmentId) {
        if (departmentId == null) {
            return userStream;
        }
        return userStream.filter(u -> {
            try {
                if (u.getDepartment() == null) {
                    return false;
                }
                Integer userDeptId = u.getDepartment().getId();
                boolean matches = userDeptId != null && userDeptId.equals(departmentId);
                return matches;
            } catch (Exception ex) {
                log.warn("Lỗi khi filter theo departmentId cho user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }

    // Lọc trạng thái hoạt động của nhân viên
    public Stream<User> applyIsActiveFilter(Stream<User> userStream, Boolean isActive) {
        if (isActive == null) {
            return userStream;
        }
        return userStream.filter(u -> {
            Boolean userIsActive = u.getIsActive();
            // Xử lý trường hợp null: null được coi là false (inactive)
            if (userIsActive == null) {
                return !isActive; // Nếu user.isActive là null và filter là false thì match
            }
            return isActive.equals(userIsActive);
        });
    }

    // Lọc nhân viên theo phòng khám
    public Stream<User> applyClinicFilter(Stream<User> userStream, Integer clinicId) {
        if (clinicId == null) {
            return userStream;
        }
        return userStream.filter(u -> {
            try {
                List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(u.getId());
                return assignments != null && assignments.stream()
                    .anyMatch(a -> a != null && a.getClinic() != null &&
                            a.getClinic().getId() != null && a.getClinic().getId().equals(clinicId));
            } catch (Exception ex) {
                log.warn("Lỗi khi filter theo clinicId cho user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }

    // Lọc nhân viên theo vai trò được chọn bổ sung (ngoài filter mặc định)
    // Lưu ý: Khi filter nhân viên đã xóa (isActive=false), cần lấy cả UserRole inactive
    public Stream<User> applyRoleFilter(Stream<User> userStream, Integer roleId, Boolean isActive) {
        if (roleId == null) {
            return userStream;
        }
        return userStream.filter(u -> {
            try {
                // Nếu filter nhân viên đã xóa, lấy cả UserRole inactive
                List<sunshine_dental_care.entities.UserRole> userRoles;
                if (Boolean.FALSE.equals(isActive)) {
                    userRoles = userRoleRepo.findByUserId(u.getId());
                } else {
                    userRoles = userRoleRepo.findActiveByUserId(u.getId());
                }
                
                if (userRoles == null || userRoles.isEmpty()) {
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
                return matchesRoleId;
            } catch (Exception ex) {
                log.warn("Lỗi khi filter theo roleId cho user {}: {}", u.getId(), ex.getMessage());
                return false;
            }
        });
    }
}
