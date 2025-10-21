package sunshine_dental_care.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDto {
    private Integer productId;
    private String sku;
    private String productName;
    private String brand;
    private String productDescription;
    private Integer unit;
    private BigDecimal defaultRetailPrice;
    private String currency;
    private Boolean isTaxable;
    private BigDecimal taxCode;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> imageUrls;
    private List<String> typeNames;
}
