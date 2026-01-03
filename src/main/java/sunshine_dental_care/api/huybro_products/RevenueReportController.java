package sunshine_dental_care.api.huybro_products;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.huybro_reports.RevenueReportResponseDto;
import sunshine_dental_care.services.huybro_reports.interfaces.IRevenueReportService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class RevenueReportController {

    private final IRevenueReportService revenueReportService;

    /**
     * API 1: Lấy dữ liệu hiển thị lên Dashboard (Charts, Cards, Table)
     * URL: GET /api/reports/revenue?startDate=2023-10-01&endDate=2023-10-31&currency=VND
     */
    @GetMapping("/revenue")
    public ResponseEntity<RevenueReportResponseDto> getRevenueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "VND") String currency
    ) {
        log.info("Request Report Dashboard: start={}, end={}, currency={}", startDate, endDate, currency);

        RevenueReportResponseDto data = revenueReportService.getRevenueDashboardData(startDate, endDate, currency);
        return ResponseEntity.ok(data);
    }

    /**
     * API 2: Xuất báo cáo ra Excel
     * URL: GET /api/reports/revenue/export?startDate=...&endDate=...&currency=...
     */
    @GetMapping("/revenue/export")
    public ResponseEntity<InputStreamResource> exportRevenueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "VND") String currency
    ) {
        log.info("Request Export Excel: start={}, end={}, currency={}", startDate, endDate, currency);

        ByteArrayInputStream in = revenueReportService.exportToExcel(startDate, endDate, currency);

        // Tạo tên file dynamic theo ngày
        String fileName = String.format("revenue_report_%s.xlsx", LocalDate.now());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + fileName);

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }
}