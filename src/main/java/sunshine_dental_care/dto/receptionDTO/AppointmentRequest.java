package sunshine_dental_care.dto.receptionDTO;

import java.math.BigDecimal;
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

    private String appointmentType; // "VIP" | "STANDARD"

    private BigDecimal bookingFee; // Phí đặt lịch hẹn

    private Integer doctorId;

    @NotNull(message = "Start date time is required")
    private Instant startDateTime;

    // endDateTime sẽ được tính toán từ services duration
    private Instant endDateTime;

    private Integer roomId;

    private String status;

    private String channel;
    private String note;

    @NotNull(message = "Services list is required for appointment booking")
    private List<ServiceItemRequest> services;
}
