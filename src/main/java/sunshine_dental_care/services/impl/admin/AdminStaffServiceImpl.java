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
import sunshine_dental_care.entities.LeaveRequest;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.LeaveRequestRepo;
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
    private final LeaveRequestRepo leaveRequestRepo;

    @Override
    @Transactional(readOnly = true)
    // Lấy danh sách nhân viên admin, chỉ lấy user đang active và có thể lọc theo search (nếu có)
    public List<AdminStaffDto> getStaff(String search) {
        log.debug("Fetching admin staff list with search: {}", search);

        // Chỉ lấy user đang active (isActive = true) và lọc theo từ khóa tìm kiếm nếu có
        return employeeFilterHelper.applyMandatoryRoleFilter(userRepo.findAll().stream(), true)
                .filter(user -> matchesSearch(user, search))
                .map(this::mapToDto)
                .sorted(Comparator.comparing(AdminStaffDto::getFullName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    // Kiểm tra user có khớp với từ khóa tìm kiếm không (tên, email, sđt, username, code)
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

    // So sánh chuỗi không phân biệt hoa thường
    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    // Chuyển đổi User entity sang AdminStaffDto, lấy thêm roles và clinics liên quan
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

        // Lấy danh sách vai trò của user (roles)
        List<UserRole> roles = userRoleRepo.findActiveByUserId(user.getId());
        dto.setRoles(
                roles.stream()
                        .map(ur -> ur.getRole() != null ? ur.getRole().getRoleName() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        // Lấy danh sách clinic mà user làm việc
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(user.getId());
        dto.setClinics(
                assignments.stream()
                        .map(assign -> assign.getClinic() != null ? assign.getClinic().getClinicName() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        // Kiểm tra xem user này có đơn nghỉ việc đã được duyệt chưa
        try {
            List<LeaveRequest> resignationRequests = leaveRequestRepo.findByUserIdAndTypeAndStatusOrderByCreatedAtDesc(
                user.getId(), "RESIGNATION", "APPROVED");
            boolean hasApprovedResignation = !resignationRequests.isEmpty();
            dto.setHasApprovedResignation(hasApprovedResignation);
        } catch (Exception ex) {
            log.warn("Lỗi kiểm tra đơn nghỉ việc đã duyệt với user {}: {}", user.getId(), ex.getMessage());
            dto.setHasApprovedResignation(false);
        }

        return dto;
    }
}
