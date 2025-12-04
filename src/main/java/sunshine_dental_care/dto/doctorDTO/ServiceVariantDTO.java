package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO cho ServiceVariant (Biến thể dịch vụ)
 * Dùng để trả về thông tin biến thể dịch vụ trong ServiceDTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceVariantDTO {
    private Integer variantId;
    private String variantName;
    private Integer duration;     // Thời gian cụ thể (30p, 60p, 180p...)
    private BigDecimal price;     // Giá tiền cụ thể
    private String description;
    private String currency;
    private Boolean isActive;
}

