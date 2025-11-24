package sunshine_dental_care.repositories.reception;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Service;

@Repository
public interface ServiceRepo extends JpaRepository<Service, Integer> {

    // Lấy tất cả dịch vụ đang Active
    List<Service> findByIsActiveTrue();

    // Lấy dịch vụ theo Category (Specialty)
    List<Service> findByCategoryIgnoreCaseAndIsActiveTrue(String category);
}
