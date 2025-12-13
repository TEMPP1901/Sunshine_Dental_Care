package sunshine_dental_care.dto.receptionDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceItemRequest {
    @NotNull(message = "Service ID is required")
    private Integer serviceId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity = 1;

    private BigDecimal unitPrice;
    private BigDecimal discountPct;
    private String note;


}