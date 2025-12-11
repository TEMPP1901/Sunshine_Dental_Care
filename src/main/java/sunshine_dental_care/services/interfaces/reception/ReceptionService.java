package sunshine_dental_care.services.interfaces.reception;

import org.springframework.data.domain.Page;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.*;
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

    List<AppointmentResponse> getAppointmentsForDashboard(
            CurrentUser currentUser,
            LocalDate date,
            Integer requestedClinicId
    );

    AppointmentResponse rescheduleAppointment(
            CurrentUser currentUser,
            Integer appointmentId,
            RescheduleRequest request
    );

    Page<PatientResponse> getPatients(String keyword, int page, int size);

    PatientResponse createPatient(PatientRequest request);

    AppointmentResponse updateAppointment(Integer appointmentId, AppointmentUpdateRequest request);
}
