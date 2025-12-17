package sunshine_dental_care.services.huybro_products.interfaces;

import sunshine_dental_care.dto.huybro_inventories.*;
import sunshine_dental_care.dto.huybro_products.*;

public interface InventoryService {
    // Các hàm cũ
    ProductStockReceiptDto importStock(ProductStockReceiptCreateDto dto);
    ProductInventoryStatusDto getProductInventoryStatus(Integer productId);

    // Lấy danh sách kho cho trang View/Export
    PageResponseDto<InventoryViewDto> getInventoryPage(int page, int size, String keyword, Integer clinicId, String sortStr);

    // Cập nhật linh hoạt (Số lượng hoặc Giá hoặc Cả hai)
    InventoryViewDto updateInventoryQuantity(InventoryUpdateDto dto);

    InventoryViewDto getInventoryDetail(Integer inventoryId);

    PageResponseDto<ProductStockHistoryDto> getProductStockHistory(Integer productId, int page, int size);

    void restoreStockForCancelledInvoice(Integer clinicId, Integer productId, Integer quantityToRestore);
}