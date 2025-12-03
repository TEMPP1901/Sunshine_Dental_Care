package sunshine_dental_care.services.doctor;

import sunshine_dental_care.dto.doctorDTO.DoctorAppointmentDTO;
import sunshine_dental_care.entities.Appointment;

import java.time.Instant;
import java.util.List;

public interface DoctorAppointmentService {
    // get all appointment by id doctor
    List<DoctorAppointmentDTO> findByDoctorId(Integer id);

    // get all appointment by id doctor and status
    List<DoctorAppointmentDTO> findByDoctorIdAndStatus(Integer id, String status);

    //get detail appointment by id
    DoctorAppointmentDTO findByIdAndDoctorId(Integer appointmentId, Integer doctorId);

    // change status appointment
    void changeStatusAppointment(Integer appointmentId, String status);

    // get all appointment by id doctor and date range
    List<DoctorAppointmentDTO> findByDoctorIdAndStartDateTimeBetween(Integer doctorId, Instant startDate, Instant endDate);
}
