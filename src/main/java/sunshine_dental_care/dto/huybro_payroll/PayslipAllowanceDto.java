package sunshine_dental_care.dto.huybro_payroll;

import lombok.Builder;
import lombok.Data;
import sunshine_dental_care.entities.huybro_salary.enums.AllowanceType;
import java.math.BigDecimal;

@Data
@Builder
public class PayslipAllowanceDto {
    private Integer id;
    private String name;
    private BigDecimal amount;
    private AllowanceType type;
    private Boolean isSystemGenerated;
    private String note;
}