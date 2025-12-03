package sunshine_dental_care.entities.huybro_product_invoice;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import sunshine_dental_care.entities.huybro_products.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ProductInvoiceItems")
public class ProductInvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoiceItemId", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoiceId")
    private ProductInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productId")
    private Product product;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unitPriceBeforeTax", precision = 18, scale = 2)
    private BigDecimal unitPriceBeforeTax;

    @Column(name = "taxRatePercent", precision = 5, scale = 2)
    private BigDecimal taxRatePercent;

    @Column(name = "lineTotalAmount", precision = 18, scale = 2)
    private BigDecimal lineTotalAmount;

    @Size(max = 200)
    @Nationalized
    @Column(name = "productNameSnapshot", length = 200)
    private String productNameSnapshot;

    @Size(max = 300)
    @Nationalized
    @Column(name = "note", length = 300)
    private String note;

    @Size(max = 64)
    @Nationalized
    @Column(name = "skuSnapshot", length = 64)
    private String skuSnapshot;

    @Column(name = "taxAmount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "remainingQuantityAfterSale")
    private Integer remainingQuantityAfterSale;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

}