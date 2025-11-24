package sunshine_dental_care.services.interfaces.reception;

import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.PublicDoctorDTO;
import sunshine_dental_care.dto.receptionDTO.bookingDto.ServiceDTO;

import java.util.List;

public interface PublicService {
    List<ClinicResponse> getAllActiveClinics();
    List<ServiceDTO> getAllActiveServices();
    List<PublicDoctorDTO> getDoctorsByClinicAndSpecialty(Integer clinicId, String specialty);
}
