package sunshine_dental_care.services.interfaces.admin;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.adminDTO.AdminStaffDto;

public interface AdminStaffService {

    // Lấy danh sách nhân viên có phân trang, có thể tìm kiếm theo tên, mã, sđt, email
    Page<AdminStaffDto> getStaff(String search, int page, int size);
}
