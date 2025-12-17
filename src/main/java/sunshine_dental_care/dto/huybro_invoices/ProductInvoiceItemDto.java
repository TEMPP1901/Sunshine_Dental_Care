package sunshine_dental_care.dto.huybro_invoices;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductInvoiceItemDto implements Serializable {

    private Integer invoiceItemId;
    private Integer productId;

    // Snapshot dữ liệu sản phẩm (để đối chiếu nếu sản phẩm gốc bị sửa tên/SKU)
    private String productNameSnapshot;
    private String skuSnapshot;

    // Số liệu tài chính dòng
    private Integer quantity;
    private BigDecimal unitPriceBeforeTax;
    private BigDecimal taxRatePercent;
    private BigDecimal taxAmount;
    private BigDecimal lineTotalAmount; // (Đơn giá * SL) + Thuế

    private String note; // Ghi chú riêng cho từng món hàng (nếu có)
}