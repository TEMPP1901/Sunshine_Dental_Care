package sunshine_dental_care.services.interfaces.admin;

import java.time.LocalDate;
import java.util.List;

import sunshine_dental_care.dto.adminDTO.AdminClinicDto;
import sunshine_dental_care.dto.adminDTO.ClinicStaffDetailDto;

public interface AdminClinicService {

    // Lấy danh sách tất cả phòng khám, dùng cho admin
    List<AdminClinicDto> getAllClinics();

    // Bật/tắt trạng thái hoạt động của phòng khám
    void updateActivation(Integer clinicId, boolean active);

    // Lấy thông tin chi tiết nhân sự phòng khám theo ngày (ví dụ xem lịch làm việc)
    List<ClinicStaffDetailDto> getClinicStaffDetails(Integer clinicId, LocalDate date);
}
