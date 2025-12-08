package sunshine_dental_care.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.ProductStatisticsDto;
import sunshine_dental_care.services.interfaces.admin.AdminInventoryService;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final AdminInventoryService adminInventoryService;

 
    @GetMapping("/statistics")
    public ResponseEntity<ProductStatisticsDto> getProductStatistics() {
        return ResponseEntity.ok(adminInventoryService.getProductStatistics());
    }
}
