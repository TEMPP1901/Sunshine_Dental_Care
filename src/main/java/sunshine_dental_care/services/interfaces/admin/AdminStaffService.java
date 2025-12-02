package sunshine_dental_care.services.interfaces.admin;

import java.util.List;

import sunshine_dental_care.dto.adminDTO.AdminStaffDto;

public interface AdminStaffService {

    List<AdminStaffDto> getStaff(String search);
}

