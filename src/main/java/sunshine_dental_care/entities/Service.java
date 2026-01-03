package sunshine_dental_care.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "Services")
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "serviceId", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "serviceName", nullable = false, length = 200)
    private String serviceName;

    @Nationalized
    @Column(name = "category", length = 100)
    private String category;

    @Nationalized
    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "defaultDuration")
    private Integer defaultDuration;

    @ColumnDefault("1")
    @Column(name = "isActive", nullable = false)
    private Boolean isActive = false;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt", nullable = false)
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ServiceVariant> variants;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDefaultDuration() {
        return defaultDuration;
    }

    public void setDefaultDuration(Integer defaultDuration) {
        this.defaultDuration = defaultDuration;
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

    public List<ServiceVariant> getVariants() { return variants; }
    public void setVariants(List<ServiceVariant> variants) { this.variants = variants; }

}