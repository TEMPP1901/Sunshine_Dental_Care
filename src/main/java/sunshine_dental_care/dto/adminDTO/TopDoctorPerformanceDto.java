package sunshine_dental_care.dto.adminDTO;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopDoctorPerformanceDto {
    private Integer doctorId;
    private String doctorName;
    private Long completedAppointments;
    private BigDecimal revenue;
}
