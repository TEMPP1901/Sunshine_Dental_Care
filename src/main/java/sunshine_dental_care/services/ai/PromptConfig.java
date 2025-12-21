package sunshine_dental_care.services.ai;

import lombok.Data;
import java.util.List;

@Data
public class PromptConfig {
    private String role_definition;
    private ClinicInfo clinic_info;
    private List<String> rules;
    private List<TrainingExample> training_examples;

    @Data
    public static class ClinicInfo {
        private List<Branch> branches;
        private String hours;
    }

    @Data
    public static class Branch {
        private String name;
        private String address;
        private String hotline;
    }

    @Data
    public static class TrainingExample {
        private String user;
        private String ai_reply;
    }
}