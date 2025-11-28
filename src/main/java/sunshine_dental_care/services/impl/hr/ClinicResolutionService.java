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

/**
 * Service để resolve clinicId cho user khi không được cung cấp trong request.
 * Logic: UserClinicAssignment (primary) → UserRole.clinic → Fallback (first clinic)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicResolutionService {

    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final UserRoleRepo userRoleRepo;
    private final ClinicRepo clinicRepo;

    /**
     * Resolve clinicId cho user. Ưu tiên: providedClinicId → UserClinicAssignment → UserRole → Fallback
     * 
     * @param userId User ID cần resolve clinic
     * @param providedClinicId ClinicId được cung cấp trong request (có thể null)
     * @return Resolved clinicId
     * @throws AttendanceValidationException Nếu không thể resolve clinicId
     */
    public Integer resolveClinicId(Integer userId, Integer providedClinicId) {
        if (providedClinicId != null) {
            return providedClinicId;
        }

        // Bước 1: Tìm từ UserClinicAssignment (ưu tiên primary)
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

        // Bước 2: Tìm từ UserRole.clinic
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
                    // Skip invalid role
                }
            }
        }

        // Bước 3: Fallback - dùng clinic đầu tiên trong hệ thống
        try {
            List<Clinic> allClinics = clinicRepo.findAll();
            if (allClinics != null && !allClinics.isEmpty() && allClinics.get(0).getId() != null) {
                return allClinics.get(0).getId();
            }
        } catch (Exception e) {
            log.error("Failed to get clinics for fallback: {}", e.getMessage());
        }

        throw new AttendanceValidationException(String.format(
                "Cannot resolve clinicId for user %d. No clinic assignment, role with clinic, or clinics in system found. Please assign user to a clinic.",
                userId));
    }
}

