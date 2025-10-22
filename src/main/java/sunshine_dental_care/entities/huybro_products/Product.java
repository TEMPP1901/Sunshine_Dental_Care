package sunshine_dental_care.entities.huybro_products;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "productId", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Nationalized
    @Column(name = "productName", nullable = false, length = 200)
    private String productName;

    @Nationalized
    @Column(name = "brand", nullable = false, length = 100)
    private String brand;

    @Nationalized
    @Column(name = "productDescription", nullable = false, length = 1000)
    private String productDescription;

    @Column(name = "unit", nullable = false)
    private Integer unit;

    @Column(name = "defaultRetailPrice", nullable = false, precision = 18, scale = 2)
    private BigDecimal defaultRetailPrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "isTaxable")
    private Boolean isTaxable;

    @Column(name = "taxCode", precision = 5, scale = 2)
    private BigDecimal taxCode;

    @Column(name = "isActive")
    private Boolean isActive;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

}