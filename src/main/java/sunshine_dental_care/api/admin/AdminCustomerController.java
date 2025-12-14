package sunshine_dental_care.api.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AdminCustomerDto;
import sunshine_dental_care.services.interfaces.admin.AdminCustomerService;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final AdminCustomerService adminCustomerService;

   
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<AdminCustomerDto>> getCustomers(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(adminCustomerService.getCustomers(search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCustomerDto> getCustomerById(@PathVariable Integer id) {
        return ResponseEntity.ok(adminCustomerService.getCustomerById(id));
    }

    
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> toggleCustomerStatus(
            @PathVariable Integer id,
            @RequestBody CustomerStatusRequest request) {
        adminCustomerService.toggleCustomerStatus(id, request.getIsActive());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // Inner class cho request body
    public static class CustomerStatusRequest {
        private Boolean isActive;

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }
    }
}
