package sunshine_dental_care.repositories.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import sunshine_dental_care.entities.Service;

public interface DentalServiceRepository extends JpaRepository<Service, Integer> {
}

