package sunshine_dental_care.services.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class DentalAIClient {

    @Value("${dental.ai.gemini.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    // --- CẬP NHẬT: Dùng đúng model có trong danh sách của bạn ---
    private static final String MODEL_ID = "gemini-2.5-flash";

    // URL chuẩn
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_ID + ":generateContent";

    @PostConstruct
    public void checkConnection() {
        System.out.println("--- DENTAL AI: Khởi tạo kết nối ---");
        System.out.println("--- MODEL MỤC TIÊU: " + MODEL_ID + " ---");

        // Test nhẹ kết nối để xem API Key có hoạt động với Model này không
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_ID + "?key=" + apiKey))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("✅ KẾT NỐI THÀNH CÔNG VỚI " + MODEL_ID);
            } else {
                System.err.println("⚠️ CẢNH BÁO: Không thể kết nối với model " + MODEL_ID);
                System.err.println("Status: " + response.statusCode());
                System.err.println("Response: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Lỗi kiểm tra mạng: " + e.getMessage());
        }
    }

    public String generateContent(String promptText) {
        try {
            // 1. Tạo JSON Body
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            ObjectNode part = contents.addObject();
            part.putArray("parts").addObject().put("text", promptText);

            String requestBody = objectMapper.writeValueAsString(root);

            // 2. Gửi Request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 3. Xử lý Response
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                if (responseJson.has("candidates") && responseJson.get("candidates").size() > 0) {
                    return responseJson.path("candidates").get(0)
                            .path("content").path("parts").get(0)
                            .path("text").asText();
                } else {
                    return "{\"reply\": \"Xin lỗi, tôi chưa hiểu rõ ý bạn.\"}";
                }
            } else {
                System.err.println("--- GEMINI ERROR ---");
                System.err.println("Status: " + response.statusCode());
                System.err.println("Body: " + response.body());
                return "{\"reply\": \"Hệ thống AI đang bảo trì.\"}";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"reply\": \"Lỗi kết nối mạng.\"}";
        }
    }
}