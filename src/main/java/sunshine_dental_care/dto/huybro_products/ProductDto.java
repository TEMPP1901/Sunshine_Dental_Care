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
    // --- [NEW FIELDS FOR CLIENT VIEW] ---
    private Integer soldCount;                 // Tổng số lượng đã bán
    private Integer discountPercentage;        // % Giảm giá (nếu có)
    private List<ProductPurchaseHistoryDto> recentPurchases; // Danh sách người vừa mua
    private List<ProductDto> relatedProducts;  // Sản phẩm gợi ý
}
