package sunshine_dental_care.dto.huybro_invoices;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class FilterRevenueRequest {

    // Ngày bắt đầu (bao gồm) cho kỳ báo cáo doanh thu.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    // Ngày kết thúc (bao gồm) cho kỳ báo cáo doanh thu.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    // Tùy chọn: Lọc theo ID phòng khám/chi nhánh nơi sản phẩm được bán/xuất hàng.
    private Integer clinicId;

    // Tùy chọn: Lọc theo trạng thái hóa đơn. Thường chỉ hóa đơn 'COMPLETED' (Hoàn thành) mới tính là doanh thu.
    private String invoiceStatus;

    // Tùy chọn: Lọc theo phương thức thanh toán (ví dụ: 'COD', 'BANK_TRANSFER').
    private String paymentMethod;

    // Tùy chọn: Đơn vị tiền tệ để tính toán/tổng hợp doanh thu (ví dụ: 'VND', 'USD').
    private String currency;

    // Tùy chọn: Độ phân giải thời gian cho biểu đồ xu hướng ('DAY', 'MONTH', 'YEAR'). Mặc định là 'DAY'.
    private String resolution;
}
