package sunshine_dental_care.dto.huybro_checkout;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CheckoutInvoiceDto {

    private Integer invoiceId;

    private String invoiceCode;

    private BigDecimal subTotal;

    private BigDecimal taxTotal;

    private BigDecimal totalAmount;

    private String currency;

    private String paymentStatus;

    private String paymentMethod;

    private String paymentChannel;

    private Instant paymentCompletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String customerFullName;

    private String customerEmail;

    private String customerPhone;

    private String shippingAddress;

    private String paymentReference;

    private String notes;

    private String invoiceStatus;

    private List<CheckoutInvoiceItemDto> items;

}
