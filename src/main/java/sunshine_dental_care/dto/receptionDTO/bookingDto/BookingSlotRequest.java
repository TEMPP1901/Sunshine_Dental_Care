package sunshine_dental_care.dto.receptionDTO.bookingDto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class BookingSlotRequest {
    @NotNull(message = "Clinic ID is required")
    private Integer clinicId;

    @NotNull(message = "Doctor ID is required")
    private Integer doctorId;

    @NotNull(message = "Service ID is required")
    //TRẢ VỀ LIST ĐỂ CHỌN NHIỀU DỊCH VỤ KHI BOOKING
    private List<Integer> serviceIds;

    @NotNull(message = "Date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}