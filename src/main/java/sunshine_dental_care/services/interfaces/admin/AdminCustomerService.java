package sunshine_dental_care.services.interfaces.admin;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.adminDTO.AdminCustomerDto;

public interface AdminCustomerService {

    // Lấy danh sách khách hàng có phân trang, cho phép lọc tìm kiếm theo tên, mã, sđt, email
    Page<AdminCustomerDto> getCustomers(String search, int page, int size);

    // Lấy chi tiết khách hàng theo id
    AdminCustomerDto getCustomerById(Integer id);

    // Bật/tắt trạng thái hoạt động của khách hàng (block/unblock)
    void toggleCustomerStatus(Integer id, Boolean isActive);
}
