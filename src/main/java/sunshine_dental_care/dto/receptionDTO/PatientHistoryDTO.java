package sunshine_dental_care.dto.receptionDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientHistoryDTO {
    private Integer appointmentId;
    private Instant visitDate;       // Ngày giờ khám
    private String doctorName;       // Tên bác sĩ
    private String diagnosis;        // Chẩn đoán (Lấy từ Note)
    private String serviceNames;     // Chuỗi tên các dịch vụ (VD: "Cạo vôi, Nhổ răng")
    private BigDecimal totalAmount;  // Tổng tiền thực trả
    private String status;           // Trạng thái (COMPLETED, CANCELLED...)
}