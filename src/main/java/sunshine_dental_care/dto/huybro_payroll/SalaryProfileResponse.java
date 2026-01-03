package sunshine_dental_care.dto.huybro_payroll;

import lombok.Builder;
import lombok.Data;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;
import sunshine_dental_care.entities.huybro_salary.enums.SalaryCalculationType;
import sunshine_dental_care.entities.huybro_salary.enums.TaxType;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder // Dùng Builder cho dễ map
public class SalaryProfileResponse {
    private Integer id;

    // --- KHÔNG TRẢ VỀ OBJECT USER, CHỈ TRẢ VỀ THÔNG TIN CƠ BẢN ---
    private Integer userId;
    private String userFullName;
    private String userEmail;
    private String userCode;

    // Các trường lương
    private SalaryCalculationType calculationType;
    private TaxType taxType;
    private BigDecimal baseSalary;
    private Double standardWorkDays;
    private Integer standardShifts;
    private BigDecimal otRate;
    private BigDecimal overShiftRate;
    private BigDecimal lateDeductionRate;
    private BigDecimal insuranceAmount;

    // List phụ cấp (Dùng DTO con, không dùng Entity SalaryAllowance)
    private List<AllowanceResponseDto> allowances;

    @Data
    @Builder
    public static class AllowanceResponseDto {
        private Integer id;
        private String allowanceName;
        private BigDecimal amount;
        private AllowanceType type;
        private String note;
    }
}