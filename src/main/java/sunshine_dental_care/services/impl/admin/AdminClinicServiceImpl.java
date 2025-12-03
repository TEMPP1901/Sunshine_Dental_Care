package sunshine_dental_care.services.impl.admin;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.adminDTO.AdminClinicDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.ClinicNotFoundException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.services.interfaces.admin.AdminClinicService;

@Service
@RequiredArgsConstructor
public class AdminClinicServiceImpl implements AdminClinicService {

    private final ClinicRepo clinicRepo;

    // Lấy tất cả phòng khám (Clinic)
    @Override
    @Transactional(readOnly = true)
    public List<AdminClinicDto> getAllClinics() {
        return clinicRepo.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // Cập nhật trạng thái kích hoạt phòng khám
    @Override
    @Transactional
    public void updateActivation(Integer clinicId, boolean active) {
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new ClinicNotFoundException(clinicId));
        clinic.setIsActive(active);
        clinicRepo.save(clinic);
    }

    // Chuyển Clinic sang DTO
    private AdminClinicDto convertToDto(Clinic clinic) {
        AdminClinicDto dto = new AdminClinicDto();
        dto.setId(clinic.getId());
        dto.setClinicCode(clinic.getClinicCode());
        dto.setClinicName(clinic.getClinicName());
        dto.setAddress(clinic.getAddress());
        dto.setPhone(clinic.getPhone());
        dto.setEmail(clinic.getEmail());
        dto.setOpeningHours(clinic.getOpeningHours());
        dto.setActive(Boolean.TRUE.equals(clinic.getIsActive()));
        dto.setCreatedAt(clinic.getCreatedAt());
        dto.setUpdatedAt(clinic.getUpdatedAt());
        return dto;
    }
}
