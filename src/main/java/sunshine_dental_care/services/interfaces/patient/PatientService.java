package sunshine_dental_care.services.interfaces.patient;

import sunshine_dental_care.dto.patientDTO.PatientAppointmentResponse;
import java.util.List;

public interface PatientService {
    // Lấy danh sách lịch hẹn của tôi
    List<PatientAppointmentResponse> getMyAppointments(String email);

    // (Chuẩn bị sẵn cho bước sau) Hủy lịch hẹn
    void cancelAppointment(String email, Integer appointmentId, String reason);
}