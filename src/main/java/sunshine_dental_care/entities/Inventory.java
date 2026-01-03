package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventoryId", nullable = false)
    private Integer id;

    @Column(name = "productId", nullable = false)
    private Integer productId;

    @Column(name = "warehouseId", nullable = false)
    private Integer warehouseId;

    @ColumnDefault("0")
    @Column(name = "qtyOnHand", nullable = false)
    private Integer qtyOnHand;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getQtyOnHand() {
        return qtyOnHand;
    }

    public void setQtyOnHand(Integer qtyOnHand) {
        this.qtyOnHand = qtyOnHand;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

}