package sunshine_dental_care.repositories.hr;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.EmployeeFaceProfile;

@Repository
public interface EmployeeFaceProfileRepo extends JpaRepository<EmployeeFaceProfile, Integer> {
    
    // Tìm face profile bằng userId
    Optional<EmployeeFaceProfile> findByUserId(Integer userId);
    
    // Tìm face profiles bằng danh sách userIds (để tối ưu batch queries)
    java.util.List<EmployeeFaceProfile> findByUserIdIn(java.util.List<Integer> userIds);
    
    // Kiểm tra user đã đăng ký kênh nhận diện khuôn mặt chưa
    boolean existsByUserId(Integer userId);
}
