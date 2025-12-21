package sunshine_dental_care.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.AdminStaffDto;
import sunshine_dental_care.dto.huybro_products.PageResponseDto;
import sunshine_dental_care.services.interfaces.admin.AdminStaffService;
import sunshine_dental_care.services.hr.EmployeeCvDataService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final AdminStaffService adminStaffService;
    private final EmployeeCvDataService employeeCvDataService;

    // Lấy danh sách nhân viên (ADMIN)
    @GetMapping
    public ResponseEntity<PageResponseDto<AdminStaffDto>> getStaff(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Page<AdminStaffDto> result = adminStaffService.getStaff(search, page, size);
        return ResponseEntity.ok(new PageResponseDto<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        ));
    }

    // Lấy CV data của nhân viên (ADMIN)
    @GetMapping("/{id}/cv")
    public ResponseEntity<Map<String, Object>> getStaffCv(@org.springframework.web.bind.annotation.PathVariable Integer id) {
        log.info("Admin requesting CV data for staff {}", id);
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
}
