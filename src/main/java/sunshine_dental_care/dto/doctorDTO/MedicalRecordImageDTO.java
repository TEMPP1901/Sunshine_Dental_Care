package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecordImageDTO {
    private Integer imageId;
    private String imageUrl;
    private String description;
    private String aiTag;
    private String imagePublicId;
    private Instant createdAt;
}

