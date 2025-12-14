package sunshine_dental_care.services.impl.admin;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.AdminClinicDto;
import sunshine_dental_care.dto.adminDTO.ClinicUpdateRequestDto;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.ClinicNotFoundException;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.services.interfaces.admin.AdminClinicService;

@Service
@RequiredArgsConstructor
@Slf4j
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
        // Admin thao tác: bỏ cờ holiday để không bị restore sai
        clinic.setDeactivatedByHoliday(false);
        clinicRepo.save(clinic);
    }

    // Cập nhật thông tin phòng khám
    @Override
    @Transactional
    public AdminClinicDto updateClinic(Integer clinicId, ClinicUpdateRequestDto request) {
        Clinic clinic = clinicRepo.findById(clinicId)
                .orElseThrow(() -> new ClinicNotFoundException(clinicId));

        // Kiểm tra clinicCode có trùng với clinic khác không (trừ chính nó)
        if (request.getClinicCode() != null && !request.getClinicCode().equals(clinic.getClinicCode())) {
            Optional<Clinic> existingClinic = clinicRepo.findByClinicCode(request.getClinicCode());
            if (existingClinic.isPresent() && !existingClinic.get().getId().equals(clinicId)) {
                throw new IllegalArgumentException("Clinic code already exists: " + request.getClinicCode());
            }
        }

        // Cập nhật thông tin
        if (request.getClinicCode() != null) {
            clinic.setClinicCode(request.getClinicCode());
        }
        if (request.getClinicName() != null) {
            clinic.setClinicName(request.getClinicName());
        }
        if (request.getAddress() != null) {
            clinic.setAddress(request.getAddress());
        }
        if (request.getPhone() != null) {
            clinic.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            clinic.setEmail(request.getEmail());
        }
        if (request.getOpeningHours() != null) {
            clinic.setOpeningHours(request.getOpeningHours());
        }

        Clinic updatedClinic = clinicRepo.save(clinic);
        log.info("Updated clinic {}: {}", clinicId, updatedClinic.getClinicName());
        
        return convertToDto(updatedClinic);
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
