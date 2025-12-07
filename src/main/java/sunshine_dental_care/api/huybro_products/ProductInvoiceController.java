package sunshine_dental_care.api.huybro_products;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceDetailDto;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceListDto;
import sunshine_dental_care.dto.huybro_invoices.UpdateInvoiceStatusRequestDto;
import sunshine_dental_care.services.huybro_products.interfaces.ProductInvoiceService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class ProductInvoiceController {

    private final ProductInvoiceService productInvoiceService;

    /**
     * 1. Lấy danh sách hóa đơn
     * - Mặc định page = 0
     * - Mặc định size = 8 (theo yêu cầu)
     * - Sắp xếp: Mới nhất lên đầu (createdAt DESC)
     */
    @GetMapping
    public ResponseEntity<Page<ProductInvoiceListDto>> getInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size, // Cập nhật size = 8
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        // Tạo Pageable với quy tắc sắp xếp: Ngày tạo giảm dần (Mới nhất -> Cũ nhất)
        Pageable pageable = PageRequest.of(page, size);

        Page<ProductInvoiceListDto> result = productInvoiceService.getInvoices(status, keyword, pageable);
        return ResponseEntity.ok(result);
    }

    // ... (Các API getDetail và updateStatus giữ nguyên như cũ)
    @GetMapping("/{id}")
    public ResponseEntity<ProductInvoiceDetailDto> getInvoiceDetail(@PathVariable Integer id) {
        ProductInvoiceDetailDto detail = productInvoiceService.getInvoiceDetail(id);
        return ResponseEntity.ok(detail);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateInvoiceStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateInvoiceStatusRequestDto request
    ) {
        productInvoiceService.updateInvoiceStatus(id, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Invoice status updated to " + request.getNewStatus() + " successfully."
        ));
    }
}