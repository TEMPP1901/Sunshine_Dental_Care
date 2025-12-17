package sunshine_dental_care.entities.huybro_salary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.entities.huybro_salary.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "salary_profiles")
public class SalaryProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false)
    private User user;

    // [FIX]: Dùng Enum thay String
    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 20)
    private SalaryCalculationType calculationType;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PROGRESSIVE'")
    @Column(name = "tax_type", nullable = false, length = 20)
    private TaxType taxType = TaxType.PROGRESSIVE;

    @ColumnDefault("0")
    @Column(name = "base_salary", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "standard_work_days")
    private Double standardWorkDays;

    @Column(name = "standard_shifts")
    private Integer standardShifts;

    @ColumnDefault("1.5")
    @Column(name = "ot_rate", precision = 4, scale = 2)
    private BigDecimal otRate;

    @Column(name = "over_shift_rate", precision = 18, scale = 2)
    private BigDecimal overShiftRate;

    @Column(name = "late_deduction_rate", precision = 18, scale = 2)
    private BigDecimal lateDeductionRate;

    @ColumnDefault("0")
    @Column(name = "insurance_amount", precision = 18, scale = 2)
    private BigDecimal insuranceAmount;

    @OneToMany(mappedBy = "salaryProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SalaryAllowance> allowances = new ArrayList<>();

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "created_at")
    private Instant createdAt;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (otRate == null) otRate = BigDecimal.valueOf(1.5);
    }
}