package sunshine_dental_care.dto.huybro_payroll;

import lombok.Data;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;
import java.math.BigDecimal;

@Data
public class ManualItemRequest {
    private String name;          // VD: "Ứng lương đợt 1", "Thưởng nóng"
    private BigDecimal amount;    // Số tiền
    private AllowanceType type;   // INCOME hoặc DEDUCTION
    private String note;
}