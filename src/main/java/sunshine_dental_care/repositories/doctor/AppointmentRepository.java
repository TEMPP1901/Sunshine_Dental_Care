package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sunshine_dental_care.entities.Appointment;

import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {
    // Query để load appointment với appointmentServices, service và serviceVariant
    @Query("SELECT DISTINCT a FROM Appointment a " +
           "LEFT JOIN FETCH a.appointmentServices aps " +
           "LEFT JOIN FETCH aps.service " +
           "LEFT JOIN FETCH aps.serviceVariant " +
           "WHERE a.id = :appointmentId")
    Optional<Appointment> findByIdWithAppointmentServices(@Param("appointmentId") Integer appointmentId);
}

