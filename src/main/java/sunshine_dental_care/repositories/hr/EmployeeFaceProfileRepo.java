package sunshine_dental_care.repositories.hr;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.EmployeeFaceProfile;

@Repository
public interface EmployeeFaceProfileRepo extends JpaRepository<EmployeeFaceProfile, Integer> {
    
    /**
     * Tìm face profile theo userId
     */
    Optional<EmployeeFaceProfile> findByUserId(Integer userId);
    
    /**
     * Kiểm tra employee đã đăng ký face chưa
     */
    boolean existsByUserId(Integer userId);
}

