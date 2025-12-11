package sunshine_dental_care.dto.aiDTO;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal; // Import BigDecimal
import java.util.List;

@Data
@Builder
public class AIChatResponse {
    private String replyText;
    private List<ServiceSuggestion> suggestedServices;
    private List<DoctorSuggestion> suggestedDoctors;

    @Data
    @Builder
    public static class ServiceSuggestion {
        private Integer id;
        private String name;
        private BigDecimal price; // Đã sửa từ Double sang BigDecimal
        private String duration;
    }

    @Data
    @Builder
    public static class DoctorSuggestion {
        private Integer id;
        private String fullName;
        private String specialty;
        private String avatarUrl;
    }
}