package sunshine_dental_care.repositories.hr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.Department;

@Repository
public interface DepartmentRepo extends JpaRepository<Department, Integer> {
    
    // Lấy tất cả departments theo thứ tự tên
    List<Department> findAllByOrderByDepartmentNameAsc();
    
    // Tìm departments chứa từ khóa trong tên (không phân biệt hoa thường)
    @Query("SELECT d FROM Department d WHERE LOWER(d.departmentName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Department> findByDepartmentNameContainingIgnoreCase(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
