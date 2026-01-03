package sunshine_dental_care.dto.receptionDTO.bookingDto;

// CHECK XEM BUỔI SÁNG HOẶC CHIỀU CÓ TRỐNG KHÔNG CHO DỊCH VỤ STANDARD

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionAvailabilityResponse {
    private boolean morningAvailable;   // Ca Sáng
    private boolean afternoonAvailable; // Ca Chiều
    private String message;
}
