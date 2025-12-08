package sunshine_dental_care.services.interfaces.patient;

import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingRequest; // Import mới
import sunshine_dental_care.entities.Appointment;

import java.util.List;

public interface PatientService {
    List<PatientAppointmentResponse> getMyAppointments(String userIdOrEmail);
    void cancelAppointment(String userIdOrEmail, Integer appointmentId, String reason);

    // --- HÀM MỚI ---
    Appointment createAppointment(String email, BookingRequest request);
}