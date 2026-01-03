package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(name = "StockMovements")
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movementId", nullable = false)
    private Integer id;

    @Column(name = "productId", nullable = false)
    private Integer productId;

    @Column(name = "warehouseId", nullable = false)
    private Integer warehouseId;

    @Nationalized
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Nationalized
    @Column(name = "refType", length = 50)
    private String refType;

    @Column(name = "refId")
    private Integer refId;

    @Column(name = "createdBy")
    private Integer createdBy;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @Nationalized
    @Column(name = "note", length = 400)
    private String note;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Integer getRefId() {
        return refId;
    }

    public void setRefId(Integer refId) {
        this.refId = refId;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

}