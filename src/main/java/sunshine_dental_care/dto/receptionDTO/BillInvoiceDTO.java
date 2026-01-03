package sunshine_dental_care.dto.receptionDTO;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BillInvoiceDTO {
    // Thông tin phòng khám & Hóa đơn
    private String clinicName;
    private String clinicAddress;
    private String invoiceId;       // Mã hóa đơn (VD: INV-102)
    private LocalDateTime createdDate;

    // Thông tin khách hàng
    private String patientName;
    private String patientPhone;
    private String patientCode;
    private String membershipRank;  // Hạng thành viên (Gold, Diamond...)

    // Chi tiết phí
    private String appointmentType; // VIP / STANDARD
    private BigDecimal bookingFee;  // Phí cọc
    private boolean isBookingFeePaid; // Đã trả cọc chưa?

    // Danh sách dịch vụ sử dụng
    private List<BillServiceItem> services;

    // --- CÁC CON SỐ TỔNG KẾT ---
    private BigDecimal subTotal;       // Tổng tiền dịch vụ (Gốc)
    private BigDecimal discountAmount; // Số tiền được giảm giá
    private BigDecimal totalAmount;    // Tổng khách phải trả (Sau khi trừ giảm + cọc)

    private BigDecimal totalPaid;      // Khách đã trả (Cọc)
    private BigDecimal remainingBalance; // Số tiền còn thiếu cần thu tại quầy

    @Data
    @Builder
    public static class BillServiceItem {
        private String serviceName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal total;
        private String invoiceId;
    }
}