package sunshine_dental_care.repositories.reception;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.AppointmentService;

@Repository
public interface AppointmentServiceRepo extends JpaRepository<AppointmentService, Integer> {

}
