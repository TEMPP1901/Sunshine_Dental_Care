package sunshine_dental_care.dto.huybro_cart;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// FE nhận DTO này để render Order Summary hoàn chỉnh
@Data
public class CartViewDto {

    private List<CartItemDto> items;  // danh sách sản phẩm trong giỏ
    private CartTotalsDto totals;     // tổng tiền toàn giỏ
    private String invoiceCode;       // mã "preview" để FE hiển thị trước nếu không thành công thì không lưu mã ( -> sẽ được gọi lại khi gọi phương thức Invoice lưu hóa đơn thành công or thất bại thì không cần gọi lại )
    private String currency;
    private BigDecimal exchangeRateToVnd;
}
