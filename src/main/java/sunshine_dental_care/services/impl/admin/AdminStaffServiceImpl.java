package sunshine_dental_care.services.impl.admin;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.AdminStaffDto;
import sunshine_dental_care.dto.hrDTO.helper.EmployeeFilterHelper;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.services.interfaces.admin.AdminStaffService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStaffServiceImpl implements AdminStaffService {

    private final UserRepo userRepo;
    private final UserRoleRepo userRoleRepo;
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final EmployeeFilterHelper employeeFilterHelper;

    @Override
    @Transactional(readOnly = true)
    public List<AdminStaffDto> getStaff(String search) {
        log.debug("Fetching admin staff list with search: {}", search);

        return employeeFilterHelper.applyMandatoryRoleFilter(userRepo.findAll().stream())
                .filter(user -> matchesSearch(user, search))
                .map(this::mapToDto)
                .sorted(Comparator.comparing(AdminStaffDto::getFullName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(User user, String search) {
        if (search == null || search.trim().isEmpty()) {
            return true;
        }
        String keyword = search.trim().toLowerCase();
        return containsIgnoreCase(user.getFullName(), keyword)
                || containsIgnoreCase(user.getEmail(), keyword)
                || containsIgnoreCase(user.getPhone(), keyword)
                || containsIgnoreCase(user.getUsername(), keyword)
                || containsIgnoreCase(user.getCode(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private AdminStaffDto mapToDto(User user) {
        AdminStaffDto dto = new AdminStaffDto();
        dto.setId(user.getId());
        dto.setCode(user.getCode());
        dto.setFullName(user.getFullName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setActive(Boolean.TRUE.equals(user.getIsActive()));
        dto.setDepartmentName(user.getDepartment() != null ? user.getDepartment().getDepartmentName() : null);
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setAvatarUrl(user.getAvatarUrl());

        List<UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());
        dto.setRoles(
                roles.stream()
                        .map(ur -> ur.getRole() != null ? ur.getRole().getRoleName() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(user.getId());
        dto.setClinics(
                assignments.stream()
                        .map(assign -> assign.getClinic() != null ? assign.getClinic().getClinicName() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        return dto;
    }
}

