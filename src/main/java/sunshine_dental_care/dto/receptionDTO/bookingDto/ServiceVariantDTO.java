package sunshine_dental_care.dto.receptionDTO.bookingDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceVariantDTO {
    private Integer variantId;
    private String variantName;
    private Integer duration;     // Thời gian cụ thể (30p, 60p, 180p...)
    private BigDecimal price;     // Giá tiền cụ thể
    private String description;
}
