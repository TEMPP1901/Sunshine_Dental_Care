package sunshine_dental_care.dto.huybro_payroll;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import sunshine_dental_care.entities.huybro_salary.enums.PeriodStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayslipViewDto {
    private Integer id;

    // --- User Info (Flattened - Phẳng hóa) ---
    private Integer userId;
    private String userFullName;
    private String userEmail;
    private String userCode;

    // --- Cycle Info ---
    private Integer month;
    private Integer year;
    private PeriodStatus status; // DRAFT/FINALIZED

    // --- Work Data ---
    private Double actualWorkDays;
    private Integer actualShifts;
    private Double standardWorkDaysSnapshot;
    private Integer standardShiftsSnapshot;

    // --- Money (Earnings) ---
    private BigDecimal baseSalarySnapshot;
    private BigDecimal salaryAmount;
    private BigDecimal otSalaryAmount;
    private BigDecimal bonusAmount;
    private BigDecimal allowanceAmount; // Tổng phụ cấp (Income)
    private BigDecimal grossSalary;     // Tổng thu nhập

    // --- Money (Deductions) ---
    private BigDecimal latePenaltyAmount;
    private BigDecimal insuranceDeduction;
    private BigDecimal taxDeduction;
    private BigDecimal advancePayment;
    private BigDecimal otherDeductionAmount; // Tổng phụ cấp (Deduction)

    private List<PayslipAllowanceDto> allowanceDetails;

    // --- NET ---
    private BigDecimal netSalary;

    private String note;
    private Instant createdAt;
}