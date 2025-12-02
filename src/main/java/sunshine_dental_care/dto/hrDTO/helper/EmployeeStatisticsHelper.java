package sunshine_dental_care.dto.hrDTO.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeStatisticsHelper {

    private final UserRoleRepo userRoleRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;

    // Tính toán thống kê từ danh sách nhân viên đã filter
    public Map<String, Object> calculateStatistics(List<User> filteredUsers) {
        Map<String, Object> stats = new HashMap<>();

        try {
            int totalEmployees = filteredUsers.size();
            long activeEmployees = filteredUsers.stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .count();
            long inactiveEmployees = totalEmployees - activeEmployees;

            Map<String, Integer> byDepartment = groupByDepartment(filteredUsers);
            Map<String, Integer> byRole = groupByRole(filteredUsers);
            Map<String, Integer> byClinic = groupByClinic(filteredUsers);

            stats.put("totalEmployees", totalEmployees);
            stats.put("activeEmployees", (int) activeEmployees);
            stats.put("inactiveEmployees", (int) inactiveEmployees);
            stats.put("byDepartment", byDepartment);
            stats.put("byRole", byRole);
            stats.put("byClinic", byClinic);

            log.info("Statistics calculated successfully: total={}, active={}, inactive={}", 
                    totalEmployees, activeEmployees, inactiveEmployees);
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

    // Phân loại theo phòng ban
    private Map<String, Integer> groupByDepartment(List<User> users) {
        Map<String, Integer> byDepartment = new HashMap<>();
        for (User user : users) {
            if (user.getDepartment() != null) {
                try {
                    String deptName = user.getDepartment().getDepartmentName();
                    String key = deptName != null ? deptName : "Uncategorized";
                    byDepartment.put(key, byDepartment.getOrDefault(key, 0) + 1);
                } catch (Exception e) {
                    byDepartment.put("Uncategorized", byDepartment.getOrDefault("Uncategorized", 0) + 1);
                }
            }
        }
        return byDepartment;
    }

    // Phân loại theo vai trò
    private Map<String, Integer> groupByRole(List<User> users) {
        Map<String, Integer> byRole = new HashMap<>();
        for (User user : users) {
            List<UserRole> userRoles = userRoleRepo.findActiveByUserId(user.getId());
            for (UserRole userRole : userRoles) {
                if (userRole.getRole() != null) {
                    try {
                        String roleName = userRole.getRole().getRoleName();
                        if (roleName != null) {
                            byRole.put(roleName, byRole.getOrDefault(roleName, 0) + 1);
                        }
                    } catch (Exception e) {
                        log.warn("Lỗi khi lấy tên vai trò cho user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
        }
        return byRole;
    }

    // Phân loại theo phòng khám
    private Map<String, Integer> groupByClinic(List<User> users) {
        Map<String, Integer> byClinic = new HashMap<>();
        for (User user : users) {
            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(user.getId());
            for (UserClinicAssignment assignment : assignments) {
                if (assignment.getClinic() != null) {
                    try {
                        String clinicName = assignment.getClinic().getClinicName();
                        if (clinicName != null) {
                            byClinic.put(clinicName, byClinic.getOrDefault(clinicName, 0) + 1);
                        }
                    } catch (Exception e) {
                        log.warn("Lỗi khi lấy tên clinic cho user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
        }
        return byClinic;
    }

    // Áp dụng filter vai trò cho thống kê (tương tự EmployeeFilterHelper nhưng dành cho thống kê)
    public Stream<User> applyRoleFilterForStatistics(Stream<User> userStream) {
        return userStream.filter(user -> {
            List<UserRole> userRoles = userRoleRepo.findActiveByUserId(user.getId());
            return userRoles.stream()
                .anyMatch(ur -> {
                    String roleName = ur.getRole() != null ? ur.getRole().getRoleName() : "";
                    return roleName != null && (
                        roleName.toUpperCase().contains("RECEPTIONIST") ||
                        roleName.toUpperCase().contains("ACCOUNTANT") ||
                        roleName.toUpperCase().contains("DOCTOR") ||
                        roleName.toUpperCase().contains("HR")
                    );
                });
        });
    }
}
