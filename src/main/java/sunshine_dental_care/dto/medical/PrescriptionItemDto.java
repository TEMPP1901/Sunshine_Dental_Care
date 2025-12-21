package sunshine_dental_care.dto.medical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrescriptionItemDto {
    private String drugName;
    private String dosage;
    private Integer quantity;
    private String usageInstruction;
}
