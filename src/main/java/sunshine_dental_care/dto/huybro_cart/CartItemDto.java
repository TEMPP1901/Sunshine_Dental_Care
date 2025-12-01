package sunshine_dental_care.dto.huybro_cart;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItemDto {

    private Integer productId;     // dùng để checkout / update
    private String sku;            // SKU hiển thị
    private String productName;    // tên sản phẩm
    private String brand;          // brand
    private String mainImageUrl;   // ảnh sản phẩm (ảnh chính)

    private Integer quantity;      // số lượng user chọn kiểm tra kỹ bên trong DB Product xem số lượng đang còn không, ưu tiên sao các phương update ( update từ việc gọi invoice thành công, update từ việc accountant gọi unit )

    // ---- đơn giá, thuế, tổng ----

    private BigDecimal unitPriceBeforeTax;   // $10.00
    private BigDecimal taxRatePercent;       // 10%
    private BigDecimal taxAmount;            // $1.00

    private BigDecimal unitPriceAfterTax;    // = unitPriceBeforeTax + taxAmount

    private BigDecimal lineTotalAmount;      // After tax * quantity

    private String currency; // USD hoặc VND
}
