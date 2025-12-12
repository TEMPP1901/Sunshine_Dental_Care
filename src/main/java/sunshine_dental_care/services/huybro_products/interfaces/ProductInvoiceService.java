package sunshine_dental_care.services.huybro_products.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import sunshine_dental_care.dto.huybro_invoices.InvoiceStatisticDto;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceDetailDto;
import sunshine_dental_care.dto.huybro_invoices.ProductInvoiceListDto;
import sunshine_dental_care.dto.huybro_invoices.UpdateInvoiceStatusRequestDto;

import java.util.List;

public interface ProductInvoiceService {

    // Xem chi tiết hóa đơn (kèm Items)
    ProductInvoiceDetailDto getInvoiceDetail(Integer invoiceId);

    // Lấy danh sách (Phân trang + Lọc trạng thái + Tìm kiếm)
    Page<ProductInvoiceListDto> getInvoices(String status, String keyword, Pageable pageable);

    List<InvoiceStatisticDto> getInvoiceStatistics();

    // Xử lý chuyển đổi trạng thái (Logic phức tạp nằm ở đây)
    void updateInvoiceStatus(Integer invoiceId, UpdateInvoiceStatusRequestDto request);
}