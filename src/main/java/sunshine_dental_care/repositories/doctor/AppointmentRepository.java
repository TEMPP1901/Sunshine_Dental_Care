package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.Appointment;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
}

