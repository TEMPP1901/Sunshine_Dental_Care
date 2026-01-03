package sunshine_dental_care.dto.huybro_invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class RevenueExportRowDto {

    // Invoice Info
    private String invoiceCode; // Mã hóa đơn
    private LocalDate invoiceDate; // Ngày xuất hóa đơn
    private String clinicName; // Tên chi nhánh (Tùy chọn)

    // Customer Info
    private String customerFullName; // Tên khách hàng
    private String customerPhone; // Số điện thoại

    // Item Info
    private String productName; // Tên sản phẩm (Snapshot)
    private String sku; // Mã SKU
    private Integer quantity; // Số lượng bán
    private BigDecimal unitPriceBeforeTax; // Giá bán trước thuế
    private BigDecimal lineTotalAmount; // Tổng tiền dòng sản phẩm (đã bao gồm thuế)

    // Financial Totals
    private BigDecimal taxRatePercent; // Phần trăm thuế
    private BigDecimal taxAmount; // Tổng tiền thuế của dòng
    private BigDecimal invoiceTotalAmount; // Tổng giá trị hóa đơn (dùng để so sánh)

    private String currency; // Đơn vị tiền tệ
    private LocalDateTime completedAt; // Thời điểm hoàn thành đơn (nếu có)
}
