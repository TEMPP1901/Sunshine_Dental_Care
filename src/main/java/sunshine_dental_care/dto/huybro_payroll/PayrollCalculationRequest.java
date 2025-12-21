package sunshine_dental_care.dto.huybro_payroll;

import lombok.Data;

import java.util.List;

@Data
public class PayrollCalculationRequest {
    private Integer month;
    private Integer year;

    // Nếu null hoặc rỗng -> Tính cho toàn bộ nhân viên
    // Nếu có ID -> Chỉ tính lại cho những người này
    private List<Integer> userIds;
}