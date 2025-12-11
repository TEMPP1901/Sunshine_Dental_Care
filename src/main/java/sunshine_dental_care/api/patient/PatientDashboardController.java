package sunshine_dental_care.api.patient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sunshine_dental_care.dto.patientDTO.PatientDashboardDTO;
import sunshine_dental_care.security.CurrentUser; // [QUAN TRỌNG] Import đúng class Security của bạn
import sunshine_dental_care.services.interfaces.patient.PatientService;

@RestController
@RequestMapping("/api/patient/dashboard")
public class PatientDashboardController {

    @Autowired
    private PatientService patientService;

    @GetMapping("/summary")
    public ResponseEntity<PatientDashboardDTO> getDashboardSummary(@AuthenticationPrincipal Object principal) {
        String identifier = null;

        // 1. Kiểm tra xem principal có phải là CurrentUser của dự án không
        if (principal instanceof CurrentUser) {
            CurrentUser user = (CurrentUser) principal;
            // Lấy ID convert sang String. Hàm findUser bên Service sẽ nhận diện đây là ID
            identifier = String.valueOf(user.userId());
        }
        // 2. Dự phòng: Nếu là UserDetails mặc định của Spring
        else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            identifier = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        // 3. Trường hợp khác (ví dụ principal là String email luôn)
        else if (principal != null) {
            identifier = principal.toString();
        }

        // Gọi service với identifier sạch (VD: "47" hoặc "email@gmail.com")
        PatientDashboardDTO data = patientService.getPatientDashboardStats(identifier);
        return ResponseEntity.ok(data);
    }
}