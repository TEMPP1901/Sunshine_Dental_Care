package sunshine_dental_care.dto.receptionDTO;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ServiceItemResponse {
    private Integer id; // ID của AppointmentServices
    private Integer serviceId;
    private String serviceName; // Tên dịch vụ (tra cứu từ ServiceRepo)
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPct;
    private BigDecimal lineTotal;
    private String note;
}