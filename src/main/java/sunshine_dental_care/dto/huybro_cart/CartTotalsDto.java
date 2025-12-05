package sunshine_dental_care.dto.huybro_cart;

import lombok.Data;
import java.math.BigDecimal;

// Tổng tiền cho toàn bộ giỏ hàng ( vì mỗi sản phẩm có mức thuế khác nhau )
@Data
public class CartTotalsDto {

    private BigDecimal subTotalBeforeTax; // tổng trước thuế
    private BigDecimal totalAfterTax;     // tổng sau thuế (FE hiển thị bold)
}
