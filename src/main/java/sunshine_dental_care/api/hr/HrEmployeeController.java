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
import sunshine_dental_care.services.hr.EmployeeCvDataService;
import sunshine_dental_care.entities.EmployeeCvData;

@RestController
@RequestMapping("/api/hr/employees")
@Slf4j
public class HrEmployeeController {

    private final HrEmployeeService hrEmployeeService;
    private final AvatarStorageService avatarStorageService;
    private final EmployeeCvDataService employeeCvDataService;

    public HrEmployeeController(HrEmployeeService hrEmployeeService, AvatarStorageService avatarStorageService,
                                EmployeeCvDataService employeeCvDataService) {
        this.hrEmployeeService = hrEmployeeService;
        this.avatarStorageService = avatarStorageService;
        this.employeeCvDataService = employeeCvDataService;
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

    // Upload CV và extract text + images bằng Tesseract OCR
    @PostMapping("/{id}/upload-cv")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> uploadCv(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("Uploading CV for employee {}: {}", id, file.getOriginalFilename());
            EmployeeCvData cvData = employeeCvDataService.uploadAndExtractCv(file, id);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("id", cvData.getId());
            response.put("userId", cvData.getUserId());
            response.put("originalFileName", cvData.getOriginalFileName());
            response.put("fileType", cvData.getFileType());
            response.put("fileSize", cvData.getFileSize());
            response.put("cvFileUrl", cvData.getCvFileUrl());
            response.put("extractedText", cvData.getExtractedText());
            response.put("extractedImages", employeeCvDataService.parseExtractedImages(cvData));
            response.put("message", "CV uploaded and extracted successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Failed to upload and extract CV: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to upload CV: " + ex.getMessage(), ex);
        }
    }

    // Lấy CV data của nhân viên
    @GetMapping("/{id}/cv")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> getCvData(@PathVariable Integer id) {
        return employeeCvDataService.getCvDataByUserId(id)
            .map(cvData -> {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("id", cvData.getId());
                response.put("userId", cvData.getUserId());
                response.put("originalFileName", cvData.getOriginalFileName());
                response.put("fileType", cvData.getFileType());
                response.put("fileSize", cvData.getFileSize());
                response.put("cvFileUrl", cvData.getCvFileUrl());
                response.put("extractedText", cvData.getExtractedText());
                response.put("extractedImages", employeeCvDataService.parseExtractedImages(cvData));
                response.put("createdAt", cvData.getCreatedAt());
                response.put("updatedAt", cvData.getUpdatedAt());
                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // Cập nhật extracted text của CV
    @PutMapping("/{id}/cv/text")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, Object>> updateCvText(
            @PathVariable Integer id,
            @RequestBody Map<String, String> request) {
        String extractedText = request.get("extractedText");
        if (extractedText == null) {
            throw new IllegalArgumentException("extractedText is required");
        }
        
        EmployeeCvData cvData = employeeCvDataService.updateExtractedText(id, extractedText);
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", cvData.getId());
        response.put("userId", cvData.getUserId());
        response.put("extractedText", cvData.getExtractedText());
        response.put("message", "CV text updated successfully");
        
        return ResponseEntity.ok(response);
    }

    // Xóa CV data của nhân viên
    @DeleteMapping("/{id}/cv")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('HR')")
    public ResponseEntity<Map<String, String>> deleteCvData(@PathVariable Integer id) {
        employeeCvDataService.deleteCvDataByUserId(id);
        Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "CV data deleted successfully");
        return ResponseEntity.ok(response);
    }
}
