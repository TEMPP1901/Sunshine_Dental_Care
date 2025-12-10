package sunshine_dental_care.services.interfaces.admin;

import java.time.LocalDate;
import java.util.List;

import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;

public interface AdminAppointmentService {
    // Lấy tất cả lịch hẹn theo ngày, nếu truyền clinicId thì lọc theo phòng khám
    List<AppointmentResponse> getAppointments(LocalDate date, Integer clinicId);
}
