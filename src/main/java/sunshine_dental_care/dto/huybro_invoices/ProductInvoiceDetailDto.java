package sunshine_dental_care.dto.huybro_invoices;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInvoiceDetailDto implements Serializable {

    // --- Thông tin định danh ---
    private Integer invoiceId;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private String invoiceStatus; // NEW, CONFIRMED, PROCESSING, COMPLETED, CANCELLED

    // --- Thông tin khách hàng (Snapshot tại thời điểm mua) ---
    private String customerFullName;
    private String customerPhone;
    private String customerEmail;
    private String shippingAddress;

    // --- Thông tin thanh toán ---
    private String paymentMethod;
    private String paymentStatus;
    private String paymentChannel;
    private Instant paymentCompletedAt;
    private String currency;

    // --- Tổng kết tài chính ---
    private BigDecimal subTotal;      // Tổng tiền hàng trước thuế
    private BigDecimal taxTotal;      // Tổng thuế
    private BigDecimal totalAmount;   // Tổng thanh toán cuối cùng

    // --- Meta data ---
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Danh sách sản phẩm (Nested DTO) ---
    private List<ProductInvoiceItemDto> items;
}
