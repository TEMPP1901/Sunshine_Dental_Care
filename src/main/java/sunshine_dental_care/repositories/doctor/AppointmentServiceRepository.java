package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.AppointmentService;

import java.util.List;

public interface AppointmentServiceRepository extends JpaRepository<AppointmentService, Integer> {
    // Query với JOIN FETCH để load Service entity cùng lúc, tránh LazyInitializationException
    @Query("SELECT aps FROM AppointmentService aps JOIN FETCH aps.service WHERE aps.appointment.id = :appointmentId")
    List<AppointmentService> findByAppointmentId(@Param("appointmentId") Integer appointmentId);
}

