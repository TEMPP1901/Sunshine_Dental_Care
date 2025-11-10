// sunshine_dental_care/dto/huybro_products/ProductFilter.java
package sunshine_dental_care.dto.huybro_products;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductFilterDto {
    private String keyword;
    private List<String> types;
    private List<String> brands;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean active;
}
