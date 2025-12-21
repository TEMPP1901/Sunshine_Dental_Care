package sunshine_dental_care.dto.huybro_invoices;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInvoiceListDto implements Serializable {
    private Integer invoiceId;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private String customerFullName;
    private String customerPhone;
    private BigDecimal totalAmount;
    private String invoiceStatus;
    private String paymentStatus;
    private String paymentMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String currency;
}