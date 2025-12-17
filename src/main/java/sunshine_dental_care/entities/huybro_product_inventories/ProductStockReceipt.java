package sunshine_dental_care.entities.huybro_product_inventories;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.huybro_products.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ProductStockReceipts")
public class ProductStockReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receiptId", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinicId", nullable = false)
    private Clinic clinic;

    @NotNull
    @Column(name = "quantityAdded", nullable = false)
    private Integer quantityAdded;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "importPrice", nullable = false, precision = 18, scale = 2)
    private BigDecimal importPrice;

    @Column(name = "profitMargin", precision = 5, scale = 2)
    private BigDecimal profitMargin;

    @Column(name = "newRetailPrice", precision = 18, scale = 2)
    private BigDecimal newRetailPrice;

    @Size(max = 3)
    @ColumnDefault("'VND'")
    @Column(name = "currency", length = 3)
    private String currency;

    @Size(max = 500)
    @Nationalized
    @Column(name = "note", length = 500)
    private String note;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

}