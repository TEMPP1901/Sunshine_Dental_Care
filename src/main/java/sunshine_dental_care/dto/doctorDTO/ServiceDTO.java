package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * DTO cho Service (Dịch vụ nha khoa)
 * Dùng để trả về thông tin dịch vụ trong MedicalRecord và Appointment
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDTO {
    private Integer id;
    private String serviceName;
    private String category;
    private String description;
    private Integer defaultDuration;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ServiceVariantDTO> variants;
}

