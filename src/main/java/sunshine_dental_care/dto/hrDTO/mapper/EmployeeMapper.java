package sunshine_dental_care.dto.hrDTO.mapper;

import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.EmployeeResponse;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.EmployeeFaceProfile;
import sunshine_dental_care.entities.LeaveRequest;
import sunshine_dental_care.entities.Role;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.RoleRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.DoctorSpecialtyRepo;
import sunshine_dental_care.repositories.hr.EmployeeFaceProfileRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeMapper {

    private final RoleRepo roleRepo;
    private final ClinicRepo clinicRepo;
    private final UserRoleRepo userRoleRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final EmployeeFaceProfileRepo faceProfileRepo;
    private final DoctorSpecialtyRepo doctorSpecialtyRepo;
    private final LeaveRequestRepo leaveRequestRepo;

    // Lấy thực thể Role, tránh lỗi proxy Hibernate
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
                role.getRoleName();
                return role;
            } catch (Exception e) {
                return null;
            }
        }
    }

    // Chuyển User sang EmployeeResponse cơ bản
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
        response.setSpecialty(user.getSpecialty()); // Backward compatibility
        
        // Lấy danh sách specialties từ bảng DoctorSpecialties
        try {
            List<String> specialties = doctorSpecialtyRepo.findByDoctorIdAndIsActiveTrue(user.getId())
                .stream()
                .map(ds -> ds.getSpecialtyName())
                .collect(Collectors.toList());
            response.setSpecialties(specialties);
        } catch (Exception ex) {
            log.warn("Error loading specialties for user {}: {}", user.getId(), ex.getMessage());
            response.setSpecialties(List.of());
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

        // Gán image khuôn mặt là avatar của user
        response.setFaceImageUrl(user.getAvatarUrl());

        faceProfileRepo.findByUserId(user.getId()).ifPresent(profile -> applyFaceProfile(response, profile));

        // Kiểm tra có đơn nghỉ việc đã được duyệt không
        setHasApprovedResignation(response, user.getId());

        return response;
    }

    // Chuyển trang User thành trang EmployeeResponse, preload map role, profile
    public Page<EmployeeResponse> mapUsersToResponses(Page<User> users) {
        List<Integer> userIds = users.getContent().stream()
            .map(User::getId)
            .toList();

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

        Map<Integer, EmployeeFaceProfile> profileMap = faceProfileRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(EmployeeFaceProfile::getUserId, profile -> profile));

        final Map<Integer, Role> roleMap = userRoleMap;
        final Map<Integer, EmployeeFaceProfile> faceProfileMap = profileMap;

        return users.map(user -> {
            EmployeeResponse response = toEmployeeResponse(user);

            Role role = roleMap.get(user.getId());
            if (role != null) {
                response.setRole(role);
            }

            if (user.getDepartment() != null) {
                try {
                    response.setDepartment(user.getDepartment());
                } catch (Exception ex) {
                    log.warn("Error initializing department for user {}: {}", user.getId(), ex.getMessage());
                }
            }

            EmployeeFaceProfile profile = faceProfileMap.get(user.getId());
            if (profile != null) {
                applyFaceProfile(response, profile);
            }

            // Kiểm tra có đơn nghỉ việc đã được duyệt không
            setHasApprovedResignation(response, user.getId());

            return response;
        });
    }

    // Chuyển User sang EmployeeResponse đầy đủ, gồm role và phòng khám đầu tiên
    public EmployeeResponse toEmployeeResponseWithDetails(User user) {
        EmployeeResponse response = toEmployeeResponse(user);

        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(user.getId());
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(user.getId());

        // Gán role chính nếu có
        if (!userRoles.isEmpty()) {
            Role role = userRoles.get(0).getRole();
            if (role != null) {
                Role realRole = getRoleFromEntity(role);
                if (realRole != null) {
                    response.setRole(realRole);
                }
            }
        }

        // Gán clinic đầu tiên nếu có phân công phòng khám
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

        faceProfileRepo.findByUserId(user.getId()).ifPresent(profile -> applyFaceProfile(response, profile));

        // Kiểm tra có đơn nghỉ việc đã được duyệt không
        setHasApprovedResignation(response, user.getId());

        return response;
    }

    // Helper method: Kiểm tra và set hasApprovedResignation
    private void setHasApprovedResignation(EmployeeResponse response, Integer userId) {
        try {
            List<LeaveRequest> resignationRequests = leaveRequestRepo.findByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
                userId, "RESIGNATION", "APPROVED");
            boolean hasApprovedResignation = !resignationRequests.isEmpty();
            response.setHasApprovedResignation(hasApprovedResignation);
        } catch (Exception ex) {
            log.warn("Error checking approved resignation for user {}: {}", userId, ex.getMessage());
            response.setHasApprovedResignation(false);
        }
    }

    // Gán embedding khuôn mặt vào EmployeeResponse
    private void applyFaceProfile(EmployeeResponse response, EmployeeFaceProfile profile) {
        if (profile == null) {
            return;
        }
        response.setFaceEmbedding(profile.getFaceEmbedding());
    }
}
