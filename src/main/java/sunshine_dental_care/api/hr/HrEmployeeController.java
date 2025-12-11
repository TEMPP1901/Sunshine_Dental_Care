package sunshine_dental_care.api.hr;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.EmployeeRequest;
import sunshine_dental_care.dto.hrDTO.EmployeeResponse;
import sunshine_dental_care.services.interfaces.hr.HrEmployeeService;
import sunshine_dental_care.services.upload_file.AvatarStorageService;

@RestController
@RequestMapping("/api/hr/employees")
@Slf4j
public class HrEmployeeController {

    private final HrEmployeeService hrEmployeeService;
    private final AvatarStorageService avatarStorageService;

    public HrEmployeeController(HrEmployeeService hrEmployeeService, AvatarStorageService avatarStorageService) {
        this.hrEmployeeService = hrEmployeeService;
        this.avatarStorageService = avatarStorageService;
    }

    // Tạo mới nhân viên
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        log.info("Creating employee: {}", request.getFullName());
        EmployeeResponse response = hrEmployeeService.createEmployee(request);
        return ResponseEntity.ok(response);
    }

    // Upload avatar cho nhân viên
    @PostMapping("/{id}/avatar")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeResponse> uploadEmployeeAvatar(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file) {
        try {
            String avatarUrl = avatarStorageService.storeAvatar(file, id);
            EmployeeRequest updateRequest = new EmployeeRequest();
            updateRequest.setAvatarUrl(avatarUrl);
            EmployeeResponse response = hrEmployeeService.updateEmployee(id, updateRequest);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to upload avatar: " + ex.getMessage(), ex);
        }
    }

    // Lấy danh sách nhân viên kèm phân trang, tìm kiếm, lọc
    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Page<EmployeeResponse>> getEmployees(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer roleId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<EmployeeResponse> employees = hrEmployeeService.getEmployees(
                search, clinicId, departmentId, roleId, isActive, page, size);
        return ResponseEntity.ok(employees);
    }

    // Lấy chi tiết nhân viên bằng id
    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeResponse> getEmployeeById(@PathVariable Integer id) {
        EmployeeResponse response = hrEmployeeService.getEmployeeById(id);
        return ResponseEntity.ok(response);
    }

    // Cập nhật thông tin nhân viên
    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable Integer id,
            @Valid @RequestBody EmployeeRequest request) {
        EmployeeResponse response = hrEmployeeService.updateEmployee(id, request);
        return ResponseEntity.ok(response);
    }

    // Khóa/mở khóa tài khoản nhân viên
    @PutMapping("/{id}/toggle-status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeResponse> toggleEmployeeStatus(
            @PathVariable Integer id,
            @RequestParam Boolean isActive,
            @RequestParam String reason) {
        EmployeeResponse response = hrEmployeeService.toggleEmployeeStatus(id, isActive, reason);
        return ResponseEntity.ok(response);
    }

    // Thống kê nhân viên theo phòng ban/chi nhánh (nếu được truyền vào)
    @GetMapping("/statistics")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) Integer departmentId) {
        Map<String, Object> stats = hrEmployeeService.getStatistics(clinicId, departmentId);
        return ResponseEntity.ok(stats);
    }

    // Xóa vĩnh viễn nhân viên (hard delete) - chỉ cho phép nếu HR xác nhận đơn nghỉ việc đã được duyệt
    @DeleteMapping("/{id}/hard-delete")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, String>> hardDeleteEmployee(
            @PathVariable Integer id,
            @RequestParam String reason) {
        hrEmployeeService.hardDeleteEmployee(id, reason);
        Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "Employee permanently deleted successfully");
        return ResponseEntity.ok(response);
    }

    // Lấy danh sách bác sĩ (endpoint public, chỉ cần xác thực)
    @GetMapping("/doctors")
    public ResponseEntity<List<EmployeeResponse>> getAllDoctors() {
        log.info("Getting all doctors (public endpoint)");
        List<EmployeeResponse> doctors = hrEmployeeService.getAllDoctors();
        return ResponseEntity.ok(doctors);
    }

    // Preview mã nhân viên sẽ được sinh (để hiển thị trên form)
    @GetMapping("/preview-code")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, String>> previewEmployeeCode() {
        log.info("Previewing employee code");
        String code = hrEmployeeService.previewEmployeeCode();
        Map<String, String> response = new java.util.HashMap<>();
        response.put("code", code);
        return ResponseEntity.ok(response);
    }
}
