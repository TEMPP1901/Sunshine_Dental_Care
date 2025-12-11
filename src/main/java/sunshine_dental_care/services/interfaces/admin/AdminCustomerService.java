package sunshine_dental_care.services.interfaces.admin;

import java.util.List;

import sunshine_dental_care.dto.adminDTO.AdminCustomerDto;

public interface AdminCustomerService {

    // Lấy tất cả khách hàng, cho phép lọc tìm kiếm theo tên, mã, sđt, email
    List<AdminCustomerDto> getCustomers(String search);

    // Lấy chi tiết khách hàng theo id
    AdminCustomerDto getCustomerById(Integer id);

    // Bật/tắt trạng thái hoạt động của khách hàng (block/unblock)
    void toggleCustomerStatus(Integer id, Boolean isActive);
}
