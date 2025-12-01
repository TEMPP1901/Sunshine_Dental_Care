package sunshine_dental_care.dto.huybro_checkout;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckoutInvoiceItemDto {

    private Integer productId;

    private String productName;

    private String sku;

    private Integer quantity;

    private BigDecimal unitPriceBeforeTax;

    private BigDecimal taxRatePercent;

    private BigDecimal taxAmount;

    private BigDecimal lineTotalAmount;

    private Integer remainingQuantityAfterSale;
}
