package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO cho AppointmentService (Dịch vụ được gán cho lịch hẹn)
 * Chứa thông tin dịch vụ và số lượng, giá, giảm giá
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentServiceDTO {
    private Integer id;
    
    private ServiceDTO service;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPct;
    private String note;
}

