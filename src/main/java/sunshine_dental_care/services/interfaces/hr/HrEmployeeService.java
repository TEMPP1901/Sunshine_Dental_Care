package sunshine_dental_care.services.interfaces.hr;

import java.util.List;
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
    
    // Xóa vĩnh viễn nhân viên (chỉ khi có đơn nghỉ việc đã được duyệt)
    void hardDeleteEmployee(Integer id, String reason);
    
    // Lấy danh sách tất cả bác sĩ (public - không yêu cầu HR role)
    List<EmployeeResponse> getAllDoctors();
}
