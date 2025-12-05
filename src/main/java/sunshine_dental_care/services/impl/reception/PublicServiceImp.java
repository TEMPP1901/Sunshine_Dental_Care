package sunshine_dental_care.services.impl.reception;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.receptionDTO.bookingDto.PublicDoctorDTO;
import sunshine_dental_care.dto.receptionDTO.bookingDto.ServiceDTO;
import sunshine_dental_care.dto.receptionDTO.bookingDto.ServiceVariantDTO;
import sunshine_dental_care.entities.DoctorSpecialty;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.reception.ServiceRepo;
import sunshine_dental_care.services.interfaces.reception.PublicService;

import java.math.BigDecimal;
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
    @Transactional(readOnly = true)
    public List<ServiceDTO> getAllActiveServices() {
        return serviceRepo.findByIsActiveTrue().stream()
                .map(s -> {
                    ServiceDTO dto = new ServiceDTO();
                    dto.setId(s.getId());
                    dto.setServiceName(s.getServiceName());
                    dto.setCategory(s.getCategory());
                    dto.setDescription(s.getDescription());

                    // --- LOGIC MỚI: Map Variants ---
                    // Lấy danh sách variants từ entity cha
                    List<ServiceVariantDTO> variantDTOs = s.getVariants().stream()
                            .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                            .map(v -> new ServiceVariantDTO(
                                    v.getId(), // variantId
                                    v.getVariantName(),
                                    v.getDuration(),
                                    v.getPrice(),
                                    v.getDescription()
                            ))
                            .collect(Collectors.toList());

                    dto.setVariants(variantDTOs);

                    // Tính toán thông tin tham khảo cho cấp Cha (Min Price & Min Duration)
                    if (!variantDTOs.isEmpty()) {
                        BigDecimal minPrice = variantDTOs.stream()
                                .map(ServiceVariantDTO::getPrice)
                                .min(BigDecimal::compareTo)
                                .orElse(BigDecimal.ZERO);
                        dto.setPrice(minPrice); // Giá "Từ..."

                        // Lấy duration của gói đầu tiên/ngắn nhất để hiển thị
                        dto.setDefaultDuration(variantDTOs.getFirst().getDuration());
                    } else {
                        dto.setPrice(BigDecimal.ZERO);
                        dto.setDefaultDuration(60);
                    }

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

        List<String> specialtyList;

        /// 1. Lấy danh sách từ bảng DoctorSpecialties (1:N)
        if (user.getDoctorSpecialties() != null && !user.getDoctorSpecialties().isEmpty()) {
            specialtyList = user.getDoctorSpecialties().stream()
                    .map(DoctorSpecialty::getSpecialtyName)
                    .collect(Collectors.toList());
        }
        // 2. Fallback về cột cũ (1:1) nếu bảng kia rỗng
        else if (user.getSpecialty() != null && !user.getSpecialty().isEmpty()) {
            specialtyList = List.of(user.getSpecialty());
        } else {
            specialtyList = List.of("General Dentist");
        }

        return new PublicDoctorDTO(
                user.getId(),
                user.getFullName(),
                user.getAvatarUrl(),
                specialtyList
        );
    }
}
