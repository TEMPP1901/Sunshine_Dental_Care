package sunshine_dental_care.repositories.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.EmployeeCvData;

@Repository
public interface EmployeeCvDataRepository extends JpaRepository<EmployeeCvData, Integer> {
    
    // Tìm bản ghi CV mới nhất của user
    Optional<EmployeeCvData> findByUserId(Integer userId);
    
    // Lấy tất cả các bản ghi CV của user (có thể có nhiều version)
    List<EmployeeCvData> findAllByUserId(Integer userId);
    
    // Xóa toàn bộ CV của user theo userId
    void deleteByUserId(Integer userId);
}
