package sunshine_dental_care.services.huybro_checkout.paypal.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaypalCreateOrderResponseDto {

    private String orderId;

    private String approveUrl;

    private BigDecimal totalAmount;

    private String currency;

    private String invoiceCode;
}