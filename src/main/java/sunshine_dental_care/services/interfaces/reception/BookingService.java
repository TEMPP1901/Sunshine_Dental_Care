package sunshine_dental_care.services.interfaces.reception;

import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.SessionAvailabilityResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {
    List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request);

    SessionAvailabilityResponse checkSessionAvailability(Integer clinicId, LocalDate date);
}
