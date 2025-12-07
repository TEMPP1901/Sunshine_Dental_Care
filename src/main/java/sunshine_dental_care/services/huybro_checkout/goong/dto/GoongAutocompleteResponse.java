package sunshine_dental_care.services.huybro_checkout.goong.dto;

import lombok.Data;
import java.util.List;

@Data
public class GoongAutocompleteResponse {
    private List<Prediction> predictions;
    private String status;

    @Data
    public static class Prediction {
        private String description; // Địa chỉ hiển thị (VD: 115 Bầu Điều...)
        private String place_id;    // ID địa điểm
        private StructuredFormatting structured_formatting;
    }

    @Data
    public static class StructuredFormatting {
        private String main_text;
        private String secondary_text;
    }
}