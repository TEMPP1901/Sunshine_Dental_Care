package sunshine_dental_care.dto.receptionDTO;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentRequest {

    @NotNull(message = "Clinic ID is required")
    private Integer clinicId;

    @NotNull(message = "Patient ID is required")
    private Integer patientId;

    @NotNull(message = "Doctor ID is required")
    private Integer doctorId;

    @NotNull(message = "Start date time is required")
    private Instant startDateTime;

    // endDateTime sẽ được tính toán từ services duration, nhưng có thể được gửi lên
    private Instant endDateTime;

    private Integer roomId;

    @NotNull(message = "Status is required")
    private String status;

    private String channel;
    private String note;

    @NotNull(message = "Services list is required for appointment booking")
    private List<ServiceItemRequest> services;
}
