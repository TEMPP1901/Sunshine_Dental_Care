package sunshine_dental_care.services.impl.reception;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.PublicDoctorDTO;
import sunshine_dental_care.dto.receptionDTO.bookingDto.ServiceDTO;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.reception.ServiceRepo;
import sunshine_dental_care.services.interfaces.reception.PublicService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicServiceImp implements PublicService {

    private final ClinicRepo clinicRepo;
    private final ServiceRepo serviceRepo;
    private final UserRepo userRepo;

    @Override
    public List<ClinicResponse> getAllActiveClinics() {
        return clinicRepo.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .map(c -> new ClinicResponse(c.getId(), c.getClinicName(), c.getClinicCode()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ServiceDTO> getAllActiveServices() {
        return serviceRepo.findByIsActiveTrue().stream()
                .map(s -> {
                    ServiceDTO dto = new ServiceDTO();
                    dto.setId(s.getId());
                    dto.setServiceName(s.getServiceName());
                    dto.setCategory(s.getCategory());
                    dto.setDefaultDuration(s.getDefaultDuration());
                    dto.setDescription(s.getDescription());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PublicDoctorDTO> getDoctorsByClinicAndSpecialty(Integer clinicId, String specialty) {
        List<User> doctors;

        // Trường hợp 1: Không có chuyên khoa -> Lấy tất cả bác sĩ ở Clinic đó
        if (specialty == null || specialty.trim().isEmpty()) {
            doctors = userRepo.findDoctorsByClinicId(clinicId); // Gọi hàm 1 tham số
        }
        // 2. Nếu có chuyên khoa (Frontend gửi dạng chuỗi "Ortho,Implant" hoặc 1 cái)
        else {
            // Tách chuỗi thành List (đề phòng FE gửi dạng "A,B")
            List<String> specialties = List.of(specialty.split(","));
            long count = specialties.size();

            // Gọi hàm mới trong Repo
            doctors = userRepo.findDoctorsBySpecialties(clinicId, specialties, count);
        }

        return doctors.stream()
                .map(this::mapToPublicDoctorDTO)
                .collect(Collectors.toList());
    }

    // Helper map Entity -> DTO
    private PublicDoctorDTO mapToPublicDoctorDTO(User user) {

        String displaySpecialty = "General Dentist"; // Giá trị mặc định

        // 1. Ưu tiên lấy từ bảng DoctorSpecialties (1:N)
        if (user.getDoctorSpecialties() != null && !user.getDoctorSpecialties().isEmpty()) {
            // Lấy cái đầu tiên làm đại diện hiển thị
            displaySpecialty = user.getDoctorSpecialties().getFirst().getSpecialtyName();
        }
        // 2. Fallback về cột cũ (1:1) nếu bảng kia rỗng
        else if (user.getSpecialty() != null && !user.getSpecialty().isEmpty()) {
            displaySpecialty = user.getSpecialty();
        }

        return new PublicDoctorDTO(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                displaySpecialty // Trả về chuyên khoa lấy được từ bảng con
        );
    }
}
