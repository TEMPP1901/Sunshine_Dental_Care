package sunshine_dental_care.services.interfaces.patient;

import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import sunshine_dental_care.dto.patientDTO.PatientDashboardDTO; // Import mới
import sunshine_dental_care.dto.patientDTO.PatientProfileDTO;
import sunshine_dental_care.dto.patientDTO.UpdatePatientProfileRequest;
import sunshine_dental_care.dto.receptionDTO.bookingDto.BookingRequest;
import sunshine_dental_care.entities.Appointment;

import java.util.List;

public interface PatientService {
    List<PatientAppointmentResponse> getMyAppointments(String userIdOrEmail);
    void cancelAppointment(String userIdOrEmail, Integer appointmentId, String reason);
    Appointment createAppointment(String email, BookingRequest request);

    // --- HÀM MỚI DASHBOARD ---
    PatientDashboardDTO getPatientDashboardStats(String userIdOrEmail);

    // --- HÀM MỚI ---
    PatientProfileDTO getPatientProfile(String userIdOrEmail);
    void updatePatientProfile(String userIdOrEmail, UpdatePatientProfileRequest request);
}