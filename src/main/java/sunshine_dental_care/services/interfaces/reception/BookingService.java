package sunshine_dental_care.services.interfaces.reception;

import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;

import java.util.List;

public interface BookingService {
    List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request);
}
