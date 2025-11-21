package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentServiceDTO {
    private Integer id;
    private Integer serviceId;
    private String serviceName;
    private String serviceCategory;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPct;
    private String note;
}

