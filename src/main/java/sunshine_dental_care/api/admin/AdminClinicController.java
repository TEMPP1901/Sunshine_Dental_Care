package sunshine_dental_care.api.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AdminClinicDto;
import sunshine_dental_care.dto.adminDTO.ClinicActivationRequestDto;
import sunshine_dental_care.services.interfaces.admin.AdminClinicService;

@RestController
@RequestMapping("/api/admin/clinics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminClinicController {

    private final AdminClinicService adminClinicService;

    @GetMapping
    public ResponseEntity<List<AdminClinicDto>> getClinics() {
        return ResponseEntity.ok(adminClinicService.getAllClinics());
    }

    @PatchMapping("/{id}/activation")
    public ResponseEntity<Void> updateActivation(
            @PathVariable Integer id,
            @Validated @RequestBody ClinicActivationRequestDto request) {
        adminClinicService.updateActivation(id, request.getActive());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

