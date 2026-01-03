package sunshine_dental_care.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AdminStaffDto;
import sunshine_dental_care.dto.huybro_products.PageResponseDto;
import sunshine_dental_care.services.interfaces.admin.AdminStaffService;

@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final AdminStaffService adminStaffService;

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
}
