package sunshine_dental_care.services.interfaces.admin;

import java.util.List;

import sunshine_dental_care.dto.adminDTO.AdminClinicDto;
import sunshine_dental_care.dto.adminDTO.ClinicUpdateRequestDto;

public interface AdminClinicService {

    // Lấy danh sách tất cả phòng khám, dùng cho admin
    List<AdminClinicDto> getAllClinics();

    // Bật/tắt trạng thái hoạt động của phòng khám
    void updateActivation(Integer clinicId, boolean active);

    // Cập nhật thông tin phòng khám
    AdminClinicDto updateClinic(Integer clinicId, ClinicUpdateRequestDto request);
}
