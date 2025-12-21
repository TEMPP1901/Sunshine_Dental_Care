package sunshine_dental_care.dto.receptionDTO;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class RescheduleRequest {
    @NotNull(message = "New Start Date Time is required")
    private Instant newStartDateTime;

    private Integer newDoctorId; // Có thể null nếu không đổi bác sĩ

    private String reason; // Lý do đổi lịch (để lưu log/note)

    private String status;
}
