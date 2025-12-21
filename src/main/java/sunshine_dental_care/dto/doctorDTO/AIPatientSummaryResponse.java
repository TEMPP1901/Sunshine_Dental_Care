package sunshine_dental_care.dto.doctorDTO;

import lombok.*;

import java.util.List;

/**
 * Response DTO for AI-generated patient summary.
 * Contains structured summary information for doctors.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AIPatientSummaryResponse {
    private String overview;
    private String attentionLevel; // LOW | MEDIUM | HIGH
    private List<DataQualityIssue> dataQualityIssues;
    private List<RiskFactor> riskFactors;
    private List<String> advisoryNotes;
    private String summaryReport;
    private String rawSummary;

    @Data
    @Builder
    public static class DataQualityIssue {
        private String issue;
        private String severity; // LOW | MEDIUM | HIGH
        private String suggestion;
    }

    @Data
    @Builder
    public static class RiskFactor {
        private String factor;
        private String evidence;
        private String impact; // MINOR | MODERATE | SIGNIFICANT
    }
}
