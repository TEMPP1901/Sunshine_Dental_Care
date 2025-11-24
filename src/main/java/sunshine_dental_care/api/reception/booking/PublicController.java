package sunshine_dental_care.api.reception.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.PublicDoctorDTO;
import sunshine_dental_care.dto.receptionDTO.bookingDto.ServiceDTO;
import sunshine_dental_care.services.interfaces.reception.PublicService;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final PublicService publicService;

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
}
