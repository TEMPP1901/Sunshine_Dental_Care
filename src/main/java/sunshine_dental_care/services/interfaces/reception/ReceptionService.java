package sunshine_dental_care.services.interfaces.reception;

import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.security.CurrentUser;

import java.time.LocalDate;
import java.util.List;

public interface ReceptionService {
    List<DoctorScheduleDto> getDoctorSchedulesForView
            (CurrentUser currentUser, LocalDate date, Integer requestClinicId);

    AppointmentResponse createNewAppointment(
            CurrentUser currentUser,
            AppointmentRequest request
    );
}
