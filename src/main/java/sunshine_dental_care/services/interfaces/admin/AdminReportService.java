package sunshine_dental_care.services.interfaces.admin;

import java.time.LocalDate;

import sunshine_dental_care.dto.adminDTO.RevenueReportDto;

public interface AdminReportService {
    // Lấy báo cáo doanh thu theo khoảng thời gian và loại tiền tệ
    RevenueReportDto getRevenueReport(LocalDate startDate, LocalDate endDate, String currency);
}
