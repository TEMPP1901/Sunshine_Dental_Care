package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO for AI-generated patient summary.
 * Contains structured summary information for doctors.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIPatientSummaryResponse {
    /**
     * Overall patient overview (demographics, basic info)
     */
    private String overview;
    
    /**
     * Important alerts (medical history, allergies, risks, etc.)
     */
    private String alerts;
    
    /**
     * Recent treatment notes and procedures
     */
    private String recentTreatments;
    
    /**
     * Raw JSON summary from AI (if needed for debugging)
     */
    private String rawSummary;
}
