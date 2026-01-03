package sunshine_dental_care.dto.aiDTO;

import lombok.Data;
import java.util.List;

@Data
public class AIChatRequest {
    private String message;
    // Thêm trường này để nhận lịch sử chat cũ (Ví dụ: 3 câu gần nhất)
    private List<MessageHistory> history;

    @Data
    public static class MessageHistory {
        private String role; // "user" hoặc "model"
        private String content;
    }
}