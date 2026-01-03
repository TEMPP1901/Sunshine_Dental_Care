package sunshine_dental_care.api.reception.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.PublicDoctorDTO;
import sunshine_dental_care.dto.receptionDTO.bookingDto.ServiceDTO;
import sunshine_dental_care.services.interfaces.reception.PublicService;
import sunshine_dental_care.services.interfaces.system.SystemConfigService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PublicController {

    private final PublicService publicService;
    private final SystemConfigService systemConfigService;

    @GetMapping("/clinics")
    public ResponseEntity<List<ClinicResponse>> getClinics() {
        return ResponseEntity.ok(publicService.getAllActiveClinics());
    }

    @GetMapping("/services")
    public ResponseEntity<List<ServiceDTO>> getServices() {
        return ResponseEntity.ok(publicService.getAllActiveServices());
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<PublicDoctorDTO>> getDoctors(
            @RequestParam Integer clinicId,
            @RequestParam(required = false) String specialty) {

        return ResponseEntity.ok(publicService.getDoctorsByClinicAndSpecialty(clinicId, specialty));
    }

    @GetMapping("/configs")
    public ResponseEntity<Map<String, String>> getPublicConfigs() {
        return ResponseEntity.ok(systemConfigService.getPublicConfigs());
    }

}
