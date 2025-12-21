package sunshine_dental_care.dto.hrDTO.helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Phân loại theo vai trò (mỗi user chỉ được đếm 1 lần cho mỗi role, không đếm trùng)
    private Map<String, Integer> groupByRole(List<User> users) {
        // Sử dụng Map<roleName, Set<userId>> để đảm bảo mỗi user chỉ được đếm 1 lần cho mỗi role
        Map<String, Set<Integer>> roleUserMap = new HashMap<>();
        
        for (User user : users) {
            // Lấy TẤT CẢ UserRole (cả active và inactive) để đảm bảo tính đúng cả inactive employees
            List<UserRole> userRoles = userRoleRepo.findByUserId(user.getId());
            for (UserRole userRole : userRoles) {
                if (userRole.getRole() != null) {
                    try {
                        String roleName = userRole.getRole().getRoleName();
                        if (roleName != null) {
                            roleUserMap.computeIfAbsent(roleName, k -> new HashSet<>()).add(user.getId());
                        }
                    } catch (Exception e) {
                        log.warn("Lỗi khi lấy tên vai trò cho user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
        }
        
        // Chuyển đổi từ Set<userId> sang số lượng (size của Set)
        Map<String, Integer> byRole = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : roleUserMap.entrySet()) {
            byRole.put(entry.getKey(), entry.getValue().size());
        }
        
        return byRole;
    }

    // Phân loại theo phòng khám (mỗi user chỉ được đếm 1 lần cho mỗi clinic, không đếm trùng)
    private Map<String, Integer> groupByClinic(List<User> users) {
        // Sử dụng Map<clinicName, Set<userId>> để đảm bảo mỗi user chỉ được đếm 1 lần cho mỗi clinic
        Map<String, Set<Integer>> clinicUserMap = new HashMap<>();
        
        for (User user : users) {
            List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(user.getId());
            for (UserClinicAssignment assignment : assignments) {
                if (assignment.getClinic() != null) {
                    try {
                        String clinicName = assignment.getClinic().getClinicName();
                        if (clinicName != null) {
                            clinicUserMap.computeIfAbsent(clinicName, k -> new HashSet<>()).add(user.getId());
                        }
                    } catch (Exception e) {
                        log.warn("Lỗi khi lấy tên clinic cho user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
        }
        
        // Chuyển đổi từ Set<userId> sang số lượng (size của Set)
        Map<String, Integer> byClinic = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : clinicUserMap.entrySet()) {
            byClinic.put(entry.getKey(), entry.getValue().size());
        }
        
        return byClinic;
    }

    // Áp dụng filter vai trò cho thống kê (lấy cả active và inactive UserRole để tính đúng cả inactive employees)
    public Stream<User> applyRoleFilterForStatistics(Stream<User> userStream) {
        return userStream.filter(user -> {
            // Lấy TẤT CẢ UserRole (cả active và inactive) để đảm bảo tính đúng cả inactive employees
            // Vì khi user inactive, UserRole của họ cũng có thể inactive
            List<UserRole> userRoles = userRoleRepo.findByUserId(user.getId());
            if (userRoles == null || userRoles.isEmpty()) {
                return false;
            }
            return userRoles.stream()
                .anyMatch(ur -> {
                    if (ur == null || ur.getRole() == null) {
                        return false;
                    }
                    String roleName = ur.getRole().getRoleName();
                    if (roleName == null || roleName.trim().isEmpty()) {
                        return false;
                    }
                    String roleUpper = roleName.toUpperCase();
                    return (
                        roleUpper.contains("RECEPTION") ||  // ✅ Bao gồm cả RECEPTION và RECEPTIONIST
                        roleUpper.contains("ACCOUNTANT") ||
                        roleUpper.contains("DOCTOR") ||
                        roleUpper.contains("HR")
                    );
                });
        });
    }
}
