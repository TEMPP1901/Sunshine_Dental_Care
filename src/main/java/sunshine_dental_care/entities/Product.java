package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "Products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "productId", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "sku", length = 100)
    private String sku;

    @Nationalized
    @Column(name = "productName", nullable = false, length = 200)
    private String productName;

    @Nationalized
    @Column(name = "category", length = 100)
    private String category;

    @Nationalized
    @Column(name = "brand", length = 100)
    private String brand;

    @Nationalized
    @Column(name = "model", length = 100)
    private String model;

    @Nationalized
    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "defaultRetailPrice", precision = 18, scale = 2)
    private BigDecimal defaultRetailPrice;

    @Nationalized
    @ColumnDefault("'VND'")
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @ColumnDefault("1")
    @Column(name = "isTaxable", nullable = false)
    private Boolean isTaxable = false;

    @ColumnDefault("1")
    @Column(name = "isActive", nullable = false)
    private Boolean isActive = false;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getDefaultRetailPrice() {
        return defaultRetailPrice;
    }

    public void setDefaultRetailPrice(BigDecimal defaultRetailPrice) {
        this.defaultRetailPrice = defaultRetailPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Boolean getIsTaxable() {
        return isTaxable;
    }

    public void setIsTaxable(Boolean isTaxable) {
        this.isTaxable = isTaxable;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

}