package sunshine_dental_care.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.DashboardStatisticsDto;
import sunshine_dental_care.services.interfaces.admin.AdminDashboardService;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * Lấy thống kê tổng quan cho Admin Dashboard.
     * Bao gồm: revenue, appointments, patients, và các metrics khác.
     * 
     * GET /api/admin/dashboard/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatisticsDto> getDashboardStatistics() {
        DashboardStatisticsDto stats = adminDashboardService.getDashboardStatistics();
        return ResponseEntity.ok(stats);
    }
}
