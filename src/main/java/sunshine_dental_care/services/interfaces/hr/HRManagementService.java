package sunshine_dental_care.services.interfaces.hr;

import java.util.List;

import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.DepartmentResponse;
import sunshine_dental_care.dto.hrDTO.RoleResponse;
import sunshine_dental_care.dto.hrDTO.RoomResponse;

public interface HRManagementService {
    
    // Lấy danh sách tất cả Departments
    List<DepartmentResponse> getAllDepartments();
    
    // Lấy danh sách tất cả Clinics (active)
    List<ClinicResponse> getAllClinics();
    
    // Lấy danh sách tất cả Roles
    List<RoleResponse> getAllRoles();
    
    // Lấy danh sách tất cả Rooms (active)
    List<RoomResponse> getAllRooms();
    
    // Lấy danh sách tất cả Holidays (read-only for HR)
    List<sunshine_dental_care.entities.Holiday> getAllHolidays();
}

