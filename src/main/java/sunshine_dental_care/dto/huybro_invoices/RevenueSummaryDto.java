package sunshine_dental_care.dto.huybro_invoices;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RevenueSummaryDto {

    // Tổng doanh thu được tạo ra trong kỳ báo cáo (chỉ tính các hóa đơn hoàn thành/đã thanh toán).
    private BigDecimal totalRevenueAmount;

    // Tổng thuế (Tax) thu được trong kỳ báo cáo.
    private BigDecimal totalTaxAmount;

    // Tổng số hóa đơn đã HOÀN THÀNH (COMPLETED).
    private Long totalCompletedInvoices;

    // Tổng số hóa đơn đã HỦY (CANCELLED) (để tính tỷ lệ hủy).
    private Long totalCancelledInvoices;

    // Tổng số hóa đơn MỚI/CHỜ XỬ LÝ (NEW/PENDING).
    private Long totalPendingInvoices;

    // Tổng số tất cả hóa đơn được tạo trong kỳ báo cáo.
    private Long totalInvoices;

    // Đơn vị tiền tệ chính được sử dụng để tổng hợp số liệu (ví dụ: VND, USD).
    private String currency;
}
