package sunshine_dental_care.services.huybro_custom;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.huybro_inventories.ClinicCustomDto;
import sunshine_dental_care.repositories.huybro_custom.ClinicRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClinicService {

    private final ClinicRepository clinicRepository;

    public List<ClinicCustomDto> getAllClinicsForDropdown() {
        return clinicRepository.findAll().stream()
                .map(clinic -> new ClinicCustomDto(
                        clinic.getId(),
                        clinic.getClinicName(),
                        clinic.getAddress()
                ))
                .collect(Collectors.toList());
    }
}