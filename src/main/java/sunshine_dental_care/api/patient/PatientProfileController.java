package sunshine_dental_care.api.patient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.patientDTO.PatientProfileDTO;
import sunshine_dental_care.dto.patientDTO.UpdatePatientProfileRequest;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.patient.PatientService;

@RestController
@RequestMapping("/api/patient/profile")
public class PatientProfileController {

    @Autowired
    private PatientService patientService;

    // Helper lấy ID từ token (Code tái sử dụng để tránh lỗi)
    private String getUserIdFromPrincipal(Object principal) {
        if (principal instanceof CurrentUser) {
            return String.valueOf(((CurrentUser) principal).getUserId());
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        return principal.toString();
    }

    @GetMapping
    public ResponseEntity<PatientProfileDTO> getProfile(@AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(patientService.getPatientProfile(getUserIdFromPrincipal(principal)));
    }

    @PutMapping
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal Object principal,
                                           @RequestBody UpdatePatientProfileRequest request) {
        patientService.updatePatientProfile(getUserIdFromPrincipal(principal), request);
        return ResponseEntity.ok("Cập nhật hồ sơ thành công!");
    }
}