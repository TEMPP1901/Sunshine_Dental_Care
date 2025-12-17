package sunshine_dental_care.api.huybro_products;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_inventories.*;
import sunshine_dental_care.dto.huybro_products.*;
import sunshine_dental_care.services.huybro_custom.ClinicService;
import sunshine_dental_care.services.huybro_products.interfaces.InventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ClinicService clinicService;

    @PostMapping("/import")
    public ResponseEntity<ProductStockReceiptDto> importStock(@RequestBody @Valid ProductStockReceiptCreateDto dto) {

        ProductStockReceiptDto result = inventoryService.importStock(dto);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/dropdown")
    public ResponseEntity<List<ClinicCustomDto>> getClinicsForDropdown() {
        return ResponseEntity.ok(clinicService.getAllClinicsForDropdown());
    }

    @GetMapping("/status/{productId}")
    public ResponseEntity<ProductInventoryStatusDto> getInventoryStatus(@PathVariable Integer productId) {
        return ResponseEntity.ok(inventoryService.getProductInventoryStatus(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryViewDto> getInventoryDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(inventoryService.getInventoryDetail(id));
    }

    @GetMapping("/page")
    public ResponseEntity<PageResponseDto<InventoryViewDto>> getInventoryPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(defaultValue = "quantity") String sortBy
    ) {
        return ResponseEntity.ok(inventoryService.getInventoryPage(page, size, keyword, clinicId, sortBy));
    }

    @PutMapping("/update-quantity")
    public ResponseEntity<InventoryViewDto> updateQuantity(@RequestBody @Valid InventoryUpdateDto dto) {
        return ResponseEntity.ok(inventoryService.updateInventoryQuantity(dto));
    }

    @GetMapping("/history/{productId}")
    public ResponseEntity<PageResponseDto<ProductStockHistoryDto>> getStockHistory(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size
    ) {
        return ResponseEntity.ok(inventoryService.getProductStockHistory(productId, page, size));
    }
}