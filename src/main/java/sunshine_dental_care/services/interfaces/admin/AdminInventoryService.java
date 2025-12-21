package sunshine_dental_care.services.interfaces.admin;

import sunshine_dental_care.dto.adminDTO.ProductStatisticsDto;

public interface AdminInventoryService {

    // Lấy thống kê tổng quan về kho sản phẩm
    ProductStatisticsDto getProductStatistics();
}
