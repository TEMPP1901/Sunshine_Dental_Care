package sunshine_dental_care.services.impl.admin;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.adminDTO.AdminCustomerDto;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.services.interfaces.admin.AdminCustomerService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminCustomerServiceImpl implements AdminCustomerService {

    private final PatientRepo patientRepo;

    @Override
    @Transactional(readOnly = true)
    public List<AdminCustomerDto> getCustomers(String search) {
        log.debug("Fetching admin customer list with search: {}", search);

        // Lấy danh sách khách hàng, có tìm kiếm hoặc không
        List<Patient> patients;
        if (search != null && !search.trim().isEmpty()) {
            Pageable pageable = PageRequest.of(0, 1000); // Lấy tối đa 1000 bản ghi nếu tìm kiếm
            Page<Patient> patientPage = patientRepo.searchPatients(search.trim(), pageable);
            patients = patientPage.getContent();
        } else {
            patients = patientRepo.findAll(); // Lấy toàn bộ khách hàng
        }

        return patients.stream()
                .map(this::mapToDto)
                .sorted(Comparator.comparing(AdminCustomerDto::getFullName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCustomerDto getCustomerById(Integer id) {
        log.debug("Fetching customer by id: {}", id);
        Patient patient = patientRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Customer not found with id: " + id));
        return mapToDto(patient);
    }

    @Override
    @Transactional
    public void toggleCustomerStatus(Integer id, Boolean isActive) {
        log.info("Toggling customer status: {} to {}", id, isActive);
        // Đổi trạng thái hoạt động cho khách hàng
        Patient patient = patientRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Customer not found with id: " + id));
        
        patient.setIsActive(isActive);
        patientRepo.save(patient);
        
        log.info("Customer {} status changed to {}", id, isActive);
    }

    // Chuyển đổi Patient sang AdminCustomerDto cho giao diện admin
    private AdminCustomerDto mapToDto(Patient patient) {
        AdminCustomerDto dto = new AdminCustomerDto();
        dto.setId(patient.getId());
        dto.setPatientCode(patient.getPatientCode());
        dto.setFullName(patient.getFullName());
        dto.setGender(patient.getGender());
        dto.setDateOfBirth(patient.getDateOfBirth());
        dto.setPhone(patient.getPhone());
        dto.setEmail(patient.getEmail());
        dto.setAddress(patient.getAddress());
        dto.setIsActive(patient.getIsActive());
        dto.setCreatedAt(patient.getCreatedAt());
        dto.setUpdatedAt(patient.getUpdatedAt());
        
        // Nếu có liên kết với User thì set userId
        if (patient.getUser() != null) {
            dto.setUserId(patient.getUser().getId());
        }

        return dto;
    }
}
