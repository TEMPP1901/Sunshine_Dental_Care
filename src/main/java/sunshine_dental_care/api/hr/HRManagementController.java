package sunshine_dental_care.api.hr;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.DepartmentResponse;
import sunshine_dental_care.dto.hrDTO.RoleResponse;
import sunshine_dental_care.dto.hrDTO.RoomResponse;
import sunshine_dental_care.services.interfaces.hr.HRManagementService;

@RestController
@RequestMapping("/api/hr/management")
@RequiredArgsConstructor
public class HRManagementController {
    
    private final HRManagementService hrManagementService;
    
    // Lấy danh sách tất cả Departments
    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('DOCTOR', 'HR', 'RECEPTIONIST', 'ACCOUNTANT')")
    public ResponseEntity<List<DepartmentResponse>> getDepartments() {
        List<DepartmentResponse> departments = hrManagementService.getAllDepartments();
        return ResponseEntity.ok(departments);
    }
    
    // Lấy danh sách tất cả Clinics
    @GetMapping("/clinics")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<ClinicResponse>> getClinics() {
        List<ClinicResponse> clinics = hrManagementService.getAllClinics();
        return ResponseEntity.ok(clinics);
    }
    
    // Lấy danh sách tất cả Roles
    @GetMapping("/roles")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<RoleResponse>> getRoles() {
        List<RoleResponse> roles = hrManagementService.getAllRoles();
        return ResponseEntity.ok(roles);
    }
    
    // Lấy danh sách tất cả Rooms (active)
    @GetMapping("/rooms")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<RoomResponse>> getRooms() {
        List<RoomResponse> rooms = hrManagementService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }
}

