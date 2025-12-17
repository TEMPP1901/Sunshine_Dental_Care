package sunshine_dental_care.entities.huybro_salary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "payslip_allowances")
public class PayslipAllowance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payslip_id", nullable = false)
    private PayslipsSnapshot payslip;

    @Nationalized
    @Column(name = "allowance_name", nullable = false, length = 100)
    private String allowanceName;

    @ColumnDefault("0")
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AllowanceType type;

    // QUAN TRỌNG: Cờ phân biệt nguồn gốc
    @ColumnDefault("0")
    @Column(name = "is_system_generated", nullable = false)
    private Boolean isSystemGenerated = false;

    @Nationalized
    @Column(name = "note")
    private String note;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}