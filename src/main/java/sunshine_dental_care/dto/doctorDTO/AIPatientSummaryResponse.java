package sunshine_dental_care.dto.doctorDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIPatientSummaryResponse {
    private String overview;
    private String alerts;
    private String recentTreatments;
    private String rawSummary; // Optional: giữ nguyên AI response gốc
}