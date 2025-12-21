package sunshine_dental_care.services.impl.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.UserClinicAssignment;
import sunshine_dental_care.entities.UserRole;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRoleRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicResolutionService {
    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final UserRoleRepo userRoleRepo;
    private final ClinicRepo clinicRepo;

    public Integer resolveClinicId(Integer userId, Integer providedClinicId) {
        if (providedClinicId != null) {
            // Nếu truyền lên một clinic cụ thể, bắt buộc phải là clinic đang active
            Clinic provided = clinicRepo.findById(providedClinicId)
                    .orElseThrow(() -> new AttendanceValidationException("Clinic not found: " + providedClinicId));
            if (!Boolean.TRUE.equals(provided.getIsActive())) {
                throw new AttendanceValidationException(
                        String.format("Clinic %d is inactive. Please choose another clinic.", providedClinicId));
            }
            return providedClinicId;
        }

        // lấy clinic từ UserClinicAssignment (ưu tiên assignment primary)
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(userId);
        if (assignments != null && !assignments.isEmpty()) {
            Optional<UserClinicAssignment> primaryAssignment = assignments.stream()
                    .filter(a -> a != null && Boolean.TRUE.equals(a.getIsPrimary())
                            && a.getClinic() != null
                            && Boolean.TRUE.equals(a.getClinic().getIsActive()))
                    .findFirst();
            // Nếu không có primary active, lấy assignment đầu tiên nhưng phải active
            UserClinicAssignment selectedAssignment = primaryAssignment.orElseGet(() -> assignments.stream()
                    .filter(a -> a != null && a.getClinic() != null && Boolean.TRUE.equals(a.getClinic().getIsActive()))
                    .findFirst()
                    .orElse(null));
            if (selectedAssignment != null) {
                Clinic clinic = selectedAssignment.getClinic();
                if (clinic != null && clinic.getId() != null) {
                    return clinic.getId();
                }
            }
        }

        // lấy clinic từ userRole (nếu có)
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        if (userRoles != null && !userRoles.isEmpty()) {
            for (UserRole userRole : userRoles) {
                if (userRole == null) continue;
                try {
                    Clinic clinic = userRole.getClinic();
                    if (clinic != null && clinic.getId() != null && Boolean.TRUE.equals(clinic.getIsActive())) {
                        return clinic.getId();
                    }
                } catch (Exception e) {
                    // bỏ qua role lỗi hoặc không có clinic
                }
            }
        }

        // fallback: chọn clinic đầu tiên trong hệ thống
        try {
            List<Clinic> allClinics = clinicRepo.findAll();
            if (allClinics != null && !allClinics.isEmpty()) {
                // Tìm clinic active đầu tiên, nếu không có thì lấy clinic đầu tiên
                Optional<Clinic> activeClinic = allClinics.stream()
                        .filter(c -> c.getId() != null && (c.getIsActive() == null || Boolean.TRUE.equals(c.getIsActive())))
                        .findFirst();
                
                if (activeClinic.isPresent() && activeClinic.get().getId() != null) {
                    log.warn("User {} has no clinic assignment. Using fallback clinic {} (active)", 
                            userId, activeClinic.get().getId());
                    return activeClinic.get().getId();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get clinics for fallback: {}", e.getMessage());
        }

        // Nếu vẫn không tìm thấy clinic, throw exception với message rõ ràng hơn
        throw new AttendanceValidationException(String.format(
                "Cannot resolve clinicId for user %d. No active clinic assignment or active clinics available. Please assign user to an active clinic or activate at least one clinic.",
                userId));
    }
}
