package sunshine_dental_care.dto.receptionDTO.bookingDto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TimeSlotResponse {
    private String time;       // Giờ hiển thị (VD: "08:00")
    private boolean available; // true = Sáng (Cho đặt), false = Tối (Bận/Sai cơ sở)
}
