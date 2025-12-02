package sunshine_dental_care.services.interfaces.admin;

import java.util.List;

import sunshine_dental_care.dto.adminDTO.AdminClinicDto;

public interface AdminClinicService {

    List<AdminClinicDto> getAllClinics();

    void updateActivation(Integer clinicId, boolean active);
}

