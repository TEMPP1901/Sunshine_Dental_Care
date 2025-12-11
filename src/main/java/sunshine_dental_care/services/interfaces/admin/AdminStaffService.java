package sunshine_dental_care.services.interfaces.admin;

import java.util.List;

import sunshine_dental_care.dto.adminDTO.AdminStaffDto;

public interface AdminStaffService {

    // Lấy danh sách nhân viên, có thể tìm kiếm theo tên, mã, sđt, email
    List<AdminStaffDto> getStaff(String search);
}
