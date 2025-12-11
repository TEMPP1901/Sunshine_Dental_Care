package sunshine_dental_care.dto.receptionDTO;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class AppointmentRequest {
    private Integer clinicId;
    private Integer patientId;
    private Integer doctorId;
    private Integer roomId;

    private Instant startDateTime;

    // --- [FIX LỖI] THÊM TRƯỜNG NÀY ---
    private Instant endDateTime;
    // ---------------------------------

    private String status;
    private String channel;
    private String note;

    private List<ServiceItemRequest> services;

    // Các trường cho AI / Booking VIP
    private String appointmentType; // "VIP" hoặc "STANDARD"
    private BigDecimal bookingFee;
}