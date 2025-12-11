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
            return providedClinicId; // đã có clinicId truyền vào
        }

        // lấy clinic từ UserClinicAssignment (ưu tiên assignment primary)
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(userId);
        if (assignments != null && !assignments.isEmpty()) {
            Optional<UserClinicAssignment> primaryAssignment = assignments.stream()
                    .filter(a -> a != null && Boolean.TRUE.equals(a.getIsPrimary()))
                    .findFirst();
            UserClinicAssignment selectedAssignment = primaryAssignment.orElse(assignments.get(0));
            Clinic clinic = selectedAssignment.getClinic();
            if (clinic != null && clinic.getId() != null) {
                return clinic.getId();
            }
        }

        // lấy clinic từ userRole (nếu có)
        List<UserRole> userRoles = userRoleRepo.findActiveByUserId(userId);
        if (userRoles != null && !userRoles.isEmpty()) {
            for (UserRole userRole : userRoles) {
                if (userRole == null) continue;
                try {
                    Clinic clinic = userRole.getClinic();
                    if (clinic != null && clinic.getId() != null) {
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
                
                // Nếu không có clinic active, lấy clinic đầu tiên
                if (allClinics.get(0).getId() != null) {
                    log.warn("User {} has no clinic assignment. Using fallback clinic {} (first available)", 
                            userId, allClinics.get(0).getId());
                    return allClinics.get(0).getId();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get clinics for fallback: {}", e.getMessage());
        }

        // Nếu vẫn không tìm thấy clinic, throw exception với message rõ ràng hơn
        throw new AttendanceValidationException(String.format(
                "Cannot resolve clinicId for user %d. No clinic assignment, role with clinic, or clinics in system found. Please assign user to a clinic or ensure at least one clinic exists in the system.",
                userId));
    }
}
