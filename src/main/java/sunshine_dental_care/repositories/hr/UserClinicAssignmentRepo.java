package sunshine_dental_care.repositories.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.UserClinicAssignment;

@Repository
public interface UserClinicAssignmentRepo extends JpaRepository<UserClinicAssignment, Integer> {
    
    // Lấy tất cả assignments của user
    List<UserClinicAssignment> findByUserId(Integer userId);
    
    // Lấy tất cả assignments của clinic
    List<UserClinicAssignment> findByClinicId(Integer clinicId);
    
    // Lấy assignment của user tại clinic cụ thể
    Optional<UserClinicAssignment> findByUserIdAndClinicId(Integer userId, Integer clinicId);
}
