package sunshine_dental_care.services.impl.reception;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import sunshine_dental_care.services.impl.reception.ai.DentalGeminiClient;
import sunshine_dental_care.services.interfaces.reception.ReceptionAiService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionAiServiceImpl implements ReceptionAiService {

    private final DentalGeminiClient geminiClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> processNaturalLanguageQuery(String userQuestion) {
        // BƯỚC 1: Phân loại & Lấy SQL
        String jsonResponse = generateSqlPrompt(userQuestion);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            String type = rootNode.path("type").asText().toUpperCase();
            String content = rootNode.path("content").asText();

            // TRƯỜNG HỢP 1: CHAT XÃ GIAO
            if ("CHAT".equals(type)) {
                return Map.of(
                        "status", "success",
                        "mode", "CHAT",
                        "answer", content
                );
            }

            // TRƯỜNG HỢP 2: TRUY VẤN DỮ LIỆU (SQL)
            else if ("SQL".equals(type)) {
                return executeAndSummarize(userQuestion, content);
            }

            return Map.of("status", "error", "message", "Không hiểu ý định AI.");

        } catch (Exception e) {
            log.error("❌ Lỗi xử lý AI: {}", e.getMessage());
            return Map.of("status", "error", "message", "Lỗi xử lý: " + e.getMessage());
        }
    }

    // --- LOGIC MỚI: CHẠY SQL XONG -> NHỜ AI TÓM TẮT ---
    private Map<String, Object> executeAndSummarize(String question, String sqlQuery) {
        try {
            // 1. Chạy SQL lấy dữ liệu thô
            if (!sqlQuery.trim().toUpperCase().startsWith("SELECT")) {
                return Map.of("status", "error", "message", "SQL không an toàn.");
            }
            List<Map<String, Object>> resultData = jdbcTemplate.queryForList(sqlQuery);

            // 2. Gửi dữ liệu thô + Câu hỏi gốc cho AI để nó "thổi hồn" vào câu trả lời
            String friendlyAnswer = generateFriendlySummary(question, resultData);

            // 3. Trả về cả Data (để vẽ bảng) VÀ Lời thoại (để hiện chat)
            return Map.of(
                    "status", "success",
                    "mode", "SQL",
                    "question", question,
                    "generatedSql", sqlQuery,
                    "data", resultData,      // Dữ liệu bảng
                    "answer", friendlyAnswer // Lời thoại thân thiện (NEW)
            );

        } catch (Exception e) {
            return Map.of("status", "error", "message", "Lỗi SQL: " + e.getMessage());
        }
    }

    // --- PROMPT 1: SINH SQL (Giữ nguyên logic cũ, chỉ tách hàm cho gọn) ---
    private String generateSqlPrompt(String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a Dental Assistant. Classify intent and return JSON.\n");

        // ... (Giữ nguyên phần Schema DB cũ của bạn ở đây) ...
        prompt.append("### DATABASE SCHEMA:\n");
        prompt.append("- Table Appointments (appointmentId, clinicId, patientId, doctorId, serviceId, startDateTime, endDateTime, status, total_amount, paymentStatus)\n");
        prompt.append("- Table Services (serviceId, serviceName)\n");
        prompt.append("- Table ServiceVariants (id, serviceId, variantName, price)\n");
        prompt.append("- Table Users (userId, fullName, roleId)\n");
        prompt.append("- Table Patients (patientId, fullName, phone, patientCode, gender, dateOfBirth)\n");
        prompt.append("- Table Clinics (clinicId, clinicName)\n");

        prompt.append("\n### RULES:\n");
        prompt.append("1. If intent is greeting/chat -> JSON: { \"type\": \"CHAT\", \"content\": \"Vietnamese response\" }\n");
        prompt.append("2. If intent is data query -> JSON: { \"type\": \"SQL\", \"content\": \"T-SQL query\" }\n");
        prompt.append("3. For SUM/COUNT use COALESCE(..., 0) and Aliases.\n");

        prompt.append("\nUser Input: \"").append(question).append("\"\nJSON Response:");

        return cleanJson(geminiClient.generateContent(prompt.toString()));
    }

    // --- PROMPT 2 (MỚI): BIẾN SỐ LIỆU THÀNH LỜI NÓI ---
    private String generateFriendlySummary(String question, List<Map<String, Object>> data) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Bạn là lễ tân nha khoa thân thiện. ");
            prompt.append("Người dùng hỏi: \"").append(question).append("\"\n");
            prompt.append("Hệ thống tìm thấy dữ liệu sau (JSON): ").append(objectMapper.writeValueAsString(data)).append("\n\n");

            prompt.append("Yêu cầu:\n");
            prompt.append("1. Trả lời ngắn gọn, tự nhiên bằng tiếng Việt dựa trên dữ liệu.\n");
            prompt.append("2. Nếu dữ liệu rỗng/null, hãy nói khéo là chưa có thông tin.\n");
            prompt.append("3. Nếu là danh sách, hãy tóm tắt số lượng (VD: 'Dạ, có 3 lịch hẹn...').\n");
            prompt.append("4. KHÔNG hiển thị lại JSON, chỉ đưa ra lời thoại.\n");

            // Gọi AI lần 2
            return geminiClient.generateContent(prompt.toString());

        } catch (Exception e) {
            return "Dạ, đây là kết quả tìm được ạ:"; // Fallback nếu lỗi
        }
    }

    private String cleanJson(String text) {
        if (text == null) return "{}";
        // Logic clean json cũ của bạn
        if (text.contains("{")) {
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            if (end > start) return text.substring(start, end + 1);
        }
        return text;
    }
}