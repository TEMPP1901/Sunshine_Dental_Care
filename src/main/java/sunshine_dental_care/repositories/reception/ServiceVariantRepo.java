package sunshine_dental_care.repositories.reception;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import sunshine_dental_care.entities.ServiceVariant;

import java.util.List;

@Repository
public interface ServiceVariantRepo extends JpaRepository<ServiceVariant, Integer> {
    List<ServiceVariant> findByServiceId(Integer serviceId);
}
