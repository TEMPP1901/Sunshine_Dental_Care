package sunshine_dental_care.dto.receptionDTO.bookingDto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class BookingRequest {
    @NotNull(message = "Clinic ID is required")
    private Integer clinicId;

    @NotNull(message = "Doctor ID is required")
    private Integer doctorId;

    @NotNull(message = "Start Date Time is required")
    private Instant startDateTime;

    @NotNull(message = "Service IDs are required")
    private List<Integer> serviceIds;

    private String note;
}