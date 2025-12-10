package sunshine_dental_care.services.interfaces.admin;

import sunshine_dental_care.dto.adminDTO.DashboardStatisticsDto;

public interface AdminDashboardService {

    // Lấy số liệu thống kê tổng quan dashboard cho admin
    DashboardStatisticsDto getDashboardStatistics();
}
