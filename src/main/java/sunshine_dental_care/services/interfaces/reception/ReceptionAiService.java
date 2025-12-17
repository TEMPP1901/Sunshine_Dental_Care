package sunshine_dental_care.services.interfaces.reception;

import java.util.Map;

public interface ReceptionAiService {
    /**
     * Nhận câu hỏi tiếng Việt, trả về kết quả truy vấn Database
     * @param question Câu hỏi (Ví dụ: "Hôm nay có bao nhiêu lịch hẹn?")
     * @return Map chứa thông tin: status, query, sql, data (kết quả)
     */
    Map<String, Object> processNaturalLanguageQuery(String question);
}