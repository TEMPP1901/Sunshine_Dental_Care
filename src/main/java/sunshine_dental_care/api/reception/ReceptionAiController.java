package sunshine_dental_care.api.reception;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sunshine_dental_care.services.interfaces.reception.ReceptionAiService;

import java.util.Map;

@RestController
@RequestMapping("/api/reception/ai")
@RequiredArgsConstructor
public class ReceptionAiController {

    private final ReceptionAiService receptionAiService;
    // API: Gửi câu hỏi -> Nhận Data
    // URL: POST http://localhost:8080/api/reception/ai/ask
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askData(@RequestBody Map<String, String> request) {
        // Lấy câu hỏi từ JSON gửi lên { "question": "..." }
        String question = request.get("question");

        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Câu hỏi không được để trống!"
            ));
        }

        // Gọi Service xử lý
        Map<String, Object> result = receptionAiService.processNaturalLanguageQuery(question);

        return ResponseEntity.ok(result);
    }
}
