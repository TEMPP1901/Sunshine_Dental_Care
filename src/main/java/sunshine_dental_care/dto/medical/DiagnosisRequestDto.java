package sunshine_dental_care.dto.medical;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisRequestDto {
    private Integer appointmentId;
    private String diagnosis;
}
