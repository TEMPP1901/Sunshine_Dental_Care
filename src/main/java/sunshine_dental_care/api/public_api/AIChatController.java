package sunshine_dental_care.api.public_api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sunshine_dental_care.dto.aiDTO.AIChatRequest;
import sunshine_dental_care.dto.aiDTO.AIChatResponse;
import sunshine_dental_care.services.ai.AIChatService;

@RestController
@RequestMapping("/api/public/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<AIChatResponse> chat(@RequestBody AIChatRequest request) {
        // Truyền cả message và history vào service
        return ResponseEntity.ok(aiChatService.processChat(request.getMessage(), request.getHistory()));
    }
}