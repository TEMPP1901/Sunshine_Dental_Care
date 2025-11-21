package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.AppointmentService;

import java.util.List;

public interface AppointmentServiceRepository extends JpaRepository<AppointmentService, Integer> {
    List<AppointmentService> findByAppointmentId(Integer appointmentId);
}

