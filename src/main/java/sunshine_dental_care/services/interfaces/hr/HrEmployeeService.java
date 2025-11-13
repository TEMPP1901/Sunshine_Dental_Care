package sunshine_dental_care.services.interfaces.hr;

import java.util.Map;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.hrDTO.EmployeeRequest;
import sunshine_dental_care.dto.hrDTO.EmployeeResponse;
 

public interface HrEmployeeService {
    
    // Tạo nhân viên mới
    EmployeeResponse createEmployee(EmployeeRequest request);
    
    // Xem danh sách nhân viên (có phân trang, tìm kiếm, lọc)
    Page<EmployeeResponse> getEmployees(String search, Integer clinicId, Integer departmentId, 
                                       Integer roleId, Boolean isActive, int page, int size);
    
    // Xem chi tiết nhân viên
    EmployeeResponse getEmployeeById(Integer id);
    
    // Cập nhật thông tin nhân viên
    EmployeeResponse updateEmployee(Integer id, EmployeeRequest request);
    
    // Khóa/mở khóa tài khoản
    EmployeeResponse toggleEmployeeStatus(Integer id, Boolean isActive, String reason);
    
    // Thống kê nhân viên
    Map<String, Object> getStatistics(Integer clinicId, Integer departmentId);
    
    // Xóa nhân viên (soft delete - set isActive = false)
    void deleteEmployee(Integer id, String reason);
}
