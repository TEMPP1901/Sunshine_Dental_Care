package sunshine_dental_care.dto.huybro_products;
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
    private List<ProductImageDto> image;
    private List<String> typeNames;
    private BigDecimal latestImportPrice;
    private BigDecimal latestProfitMargin;
    private Integer soldCount;
    private BigDecimal discountPercentage;
    private List<ProductPurchaseHistoryDto> recentPurchases;
    private List<ProductDto> relatedProducts;
    private BigDecimal originalPrice;
}
