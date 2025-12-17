package sunshine_dental_care.entities.huybro_product_inventories;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.huybro_products.Product;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ProductInventories")
public class ProductInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventoryId", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinicId", nullable = false)
    private Clinic clinic;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @ColumnDefault("5")
    @Column(name = "minStockLevel")
    private Integer minStockLevel;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "lastUpdated")
    private LocalDateTime lastUpdated;

}