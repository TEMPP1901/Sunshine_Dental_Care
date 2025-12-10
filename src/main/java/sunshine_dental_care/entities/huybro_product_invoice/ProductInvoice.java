package sunshine_dental_care.entities.huybro_product_invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.Patient;

@Getter
@Setter
@Entity
@Table(name = "ProductInvoices")
public class ProductInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoiceId", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinicId")
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patientId")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointmentId")
    private Appointment appointment;

    @Size(max = 50)
    @Nationalized
    @Column(name = "invoiceCode", length = 50)
    private String invoiceCode;

    @ColumnDefault("0")
    @Column(name = "subTotal", precision = 18, scale = 2)
    private BigDecimal subTotal;

    @ColumnDefault("0")
    @Column(name = "taxTotal", precision = 18, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "totalAmount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Size(max = 10)
    @Nationalized
    @ColumnDefault("N'VND'")
    @Column(name = "currency", length = 10)
    private String currency;

    @Size(max = 50)
    @Nationalized
    @Column(name = "paymentStatus", length = 50)
    private String paymentStatus;

    @ColumnDefault("CONVERT([date], sysutcdatetime())")
    @Column(name = "invoiceDate")
    private LocalDate invoiceDate;

    @Size(max = 400)
    @Nationalized
    @Column(name = "notes", length = 400)
    private String notes;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Size(max = 50)
    @NotNull
    @Nationalized
    @ColumnDefault("N'UNKNOWN'")
    @Column(name = "paymentMethod", nullable = false, length = 50)
    private String paymentMethod;

    @Size(max = 50)
    @Nationalized
    @Column(name = "paymentChannel", length = 50)
    private String paymentChannel;

    @Column(name = "paymentCompletedAt")
    private Instant paymentCompletedAt;

    @Size(max = 200)
    @Nationalized
    @Column(name = "customerFullName", length = 200)
    private String customerFullName;

    @Size(max = 120)
    @Nationalized
    @Column(name = "customerEmail", length = 120)
    private String customerEmail;

    @Size(max = 50)
    @Nationalized
    @Column(name = "customerPhone", length = 50)
    private String customerPhone;

    @Size(max = 400)
    @Nationalized
    @Column(name = "shippingAddress", length = 400)
    private String shippingAddress;

    @Size(max = 100)
    @Nationalized
    @Column(name = "paymentReference", length = 100)
    private String paymentReference;

    @Size(max = 50)
    @Nationalized
    @Transient // Column not present in DB; avoid SQL errors
    private String invoiceStatus;

}