package sunshine_dental_care.services.interfaces.reception;

import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingSlotRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.SessionAvailabilityResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.TimeSlotResponse;
import sunshine_dental_care.entities.Appointment;

import java.time.LocalDate;
import java.util.List;

public interface BookingService {

    // --- 1. PHẦN CHUNG (Cả 2 đều dùng) ---
    // Lấy danh sách các slot giờ còn trống
    List<TimeSlotResponse> getAvailableSlots(BookingSlotRequest request);

    // --- 2. PHẦN CỦA BẠN (Quan trọng để Booking) ---
    // Tạo mới một cuộc hẹn
    Appointment createAppointment(AppointmentRequest request);

    // --- 3. PHẦN CỦA LONG (Mới thêm) ---
    // Kiểm tra xem Buổi Sáng/Chiều/Tối còn chỗ không (Trả về Full/Available)
    SessionAvailabilityResponse checkSessionAvailability(Integer clinicId, LocalDate date);

    // Thêm hàm này để Controller gọi được
    void notifyBookingSuccess(Appointment appt);
}