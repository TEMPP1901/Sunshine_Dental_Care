package sunshine_dental_care.dto.medical;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedMedicalRecordDto {
    private String treatmentPlan;
    private List<PrescriptionItemDto> prescriptionNote;
    private String prescriptionNoteFormatted;
    private String note;
}
