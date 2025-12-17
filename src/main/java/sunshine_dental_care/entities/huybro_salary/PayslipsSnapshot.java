package sunshine_dental_care.entities.huybro_salary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import sunshine_dental_care.entities.User;

import java.math.BigDecimal;
import java.time.Instant; // Dùng Instant cho đồng bộ
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "payslips_snapshot")
public class PayslipsSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_cycle_id", nullable = false)
    private SalaryCycle salaryCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // --- SNAPSHOT ---
    @Column(name = "base_salary_snapshot", precision = 18, scale = 2)
    private BigDecimal baseSalarySnapshot;

    @Column(name = "standard_work_days_snapshot")
    private Double standardWorkDaysSnapshot;

    @Column(name = "standard_shifts_snapshot")
    private Integer standardShiftsSnapshot;

    // --- REAL DATA ---
    @ColumnDefault("0")
    @Column(name = "actual_work_days")
    private Double actualWorkDays;

    @ColumnDefault("0")
    @Column(name = "actual_shifts")
    private Integer actualShifts;

    @ColumnDefault("0")
    @Column(name = "total_ot_hours")
    private Double totalOtHours;

    @ColumnDefault("0")
    @Column(name = "total_late_minutes")
    private Integer totalLateMinutes;

    // --- INCOME ---
    @ColumnDefault("0")
    @Column(name = "salary_amount", precision = 18, scale = 2)
    private BigDecimal salaryAmount;

    @ColumnDefault("0")
    @Column(name = "ot_salary_amount", precision = 18, scale = 2)
    private BigDecimal otSalaryAmount;

    @ColumnDefault("0")
    @Column(name = "bonus_amount", precision = 18, scale = 2)
    private BigDecimal bonusAmount;

    @ColumnDefault("0")
    @Column(name = "allowance_amount", precision = 18, scale = 2)
    private BigDecimal allowanceAmount;

    // --- DEDUCTIONS ---
    @ColumnDefault("0")
    @Column(name = "late_penalty_amount", precision = 18, scale = 2)
    private BigDecimal latePenaltyAmount;

    @ColumnDefault("0")
    @Column(name = "insurance_deduction", precision = 18, scale = 2)
    private BigDecimal insuranceDeduction;

    @ColumnDefault("0")
    @Column(name = "advance_payment", precision = 18, scale = 2)
    private BigDecimal advancePayment;

    @ColumnDefault("0")
    @Column(name = "other_deduction_amount", precision = 18, scale = 2)
    private BigDecimal otherDeductionAmount;

    @ColumnDefault("0")
    @Column(name = "tax_deduction", precision = 18, scale = 2)
    private BigDecimal taxDeduction;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayslipAllowance> periodicAllowances = new ArrayList<>();
    // --- TOTAL ---
    @ColumnDefault("0")
    @Column(name = "gross_salary", precision = 18, scale = 2)
    private BigDecimal grossSalary;

    @ColumnDefault("0")
    @Column(name = "net_salary", precision = 18, scale = 2)
    private BigDecimal netSalary;

    @Nationalized
    @Column(name = "note", length = 500)
    private String note;

    @ColumnDefault("sysutcdatetime()")
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}