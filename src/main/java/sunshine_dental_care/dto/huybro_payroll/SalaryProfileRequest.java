package sunshine_dental_care.dto.huybro_payroll;

import lombok.Data;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;
import sunshine_dental_care.entities.huybro_salary.enums.SalaryCalculationType;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalaryProfileRequest {
    private Integer userId;
    private SalaryCalculationType calculationType; // MONTHLY or SHIFT_BASED
    private BigDecimal baseSalary;

    // Định mức (Mẫu số)
    private Double standardWorkDays; // Cho Admin
    private Integer standardShifts;  // Cho Bác sĩ

    // Các hệ số
    private BigDecimal otRate;
    private BigDecimal overShiftRate;
    private BigDecimal lateDeductionRate;
    private BigDecimal insuranceAmount;

    // Danh sách phụ cấp
    private List<AllowanceDTO> allowances;

    @Data
    public static class AllowanceDTO {
        private String allowanceName;
        private BigDecimal amount;
        private AllowanceType type; // INCOME (Cộng) or DEDUCTION (Trừ)
        private String note;
    }
}