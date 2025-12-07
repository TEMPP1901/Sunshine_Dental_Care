package sunshine_dental_care.services.huybro_reports.interfaces;

import sunshine_dental_care.dto.huybro_reports.RevenueReportResponseDto;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;

public interface IRevenueReportService {

    /**
     * Lấy dữ liệu tổng hợp cho Dashboard Report.
     * @param startDate Ngày bắt đầu (Nếu null -> Default 30 ngày gần nhất)
     * @param endDate Ngày kết thúc (Nếu null -> Default hôm nay)
     * @param targetCurrency Đơn vị tiền tệ muốn xem báo cáo ('VND' hoặc 'USD')
     * @return DTO chứa KPI, Chart Data, Top Products
     */
    RevenueReportResponseDto getRevenueDashboardData(LocalDate startDate, LocalDate endDate, String targetCurrency);

    /**
     * Xuất báo cáo ra file Excel (.xlsx).
     * @param startDate Ngày bắt đầu
     * @param endDate Ngày kết thúc
     * @param targetCurrency Đơn vị tiền tệ quy đổi
     * @return Stream dữ liệu file Excel
     */
    ByteArrayInputStream exportToExcel(LocalDate startDate, LocalDate endDate, String targetCurrency);
}