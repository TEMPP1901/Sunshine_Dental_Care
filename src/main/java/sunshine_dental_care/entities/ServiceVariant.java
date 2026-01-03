package sunshine_dental_care.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ServiceVariants")
public class ServiceVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variantId", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serviceId", nullable = false)
    @JsonIgnore
    private Service service;

    @Size(max = 200)
    @NotNull
    @Nationalized
    @Column(name = "variantName", nullable = false, length = 200)
    private String variantName;

    @Nationalized
    @Lob
    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "duration", nullable = false)
    private Integer duration;

    @NotNull
    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Size(max = 10)
    @Nationalized
    @ColumnDefault("'VND'")
    @Column(name = "currency", length = 10)
    private String currency;

    @ColumnDefault("1")
    @Column(name = "isActive")
    private Boolean isActive;

    @ColumnDefault("getdate()")
    @Column(name = "createdAt")
    private Instant createdAt;

    @ColumnDefault("getdate()")
    @Column(name = "updatedAt")
    private Instant updatedAt;

}