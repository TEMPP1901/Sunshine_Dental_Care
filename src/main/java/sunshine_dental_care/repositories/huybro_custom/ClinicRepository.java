package sunshine_dental_care.repositories.huybro_custom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.Clinic;

@Repository
public interface ClinicRepository extends JpaRepository<Clinic, Integer> {

}