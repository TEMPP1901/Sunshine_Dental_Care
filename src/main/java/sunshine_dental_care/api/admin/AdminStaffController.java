package sunshine_dental_care.api.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AdminStaffDto;
import sunshine_dental_care.services.interfaces.admin.AdminStaffService;

@RestController
@RequestMapping("/api/admin/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStaffController {

    private final AdminStaffService adminStaffService;

    // Lấy danh sách nhân viên (ADMIN)
    @GetMapping
    public ResponseEntity<List<AdminStaffDto>> getStaff(
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(adminStaffService.getStaff(search));
    }
}
