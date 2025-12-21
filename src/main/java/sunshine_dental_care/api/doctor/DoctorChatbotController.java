//package sunshine_dental_care.api.doctor;
//
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import sunshine_dental_care.dto.doctorDTO.ChatbotRequest;
//import sunshine_dental_care.dto.doctorDTO.ChatbotResponse;
//import sunshine_dental_care.services.doctor.DoctorChatbotService;
//
///**
// * Controller for Doctor Chatbot API
// *
// * ENDPOINT: /api/doctors/chatbot
// *
// * LUỒNG XỬ LÝ:
// * ============
// * 1. Doctor gửi POST request với ChatbotRequest (chứa question và optional filters)
// * 2. Controller nhận request và gọi DoctorChatbotService
// * 3. Service sẽ:
// *    - Query database dựa trên filters (patientId, recordId, appointmentId)
// *    - Build context từ dữ liệu database
// *    - Gửi context + question cho Gemini AI
// *    - Trả về response từ Gemini
// * 4. Controller trả về ChatbotResponse cho doctor
// *
// * VÍ DỤ SỬ DỤNG:
// * ==============
// *
// * 1. Hỏi về patient cụ thể:
// *    POST /api/doctors/chatbot
// *    {
// *      "question": "What is the diagnosis for this patient?",
// *      "patientId": 123
// *    }
// *
// * 2. Hỏi về medical record cụ thể:
// *    POST /api/doctors/chatbot
// *    {
// *      "question": "What services were provided in this record?",
// *      "recordId": 456
// *    }
// *
// * 3. Hỏi về appointment:
// *    POST /api/doctors/chatbot
// *    {
// *      "question": "What is the status of this appointment?",
// *      "appointmentId": 789
// *    }
// *
// * 4. Hỏi chung (không có filter):
// *    POST /api/doctors/chatbot
// *    {
// *      "question": "Show me recent medical records"
// *    }
// */
//@RestController
//@RequestMapping("/api/doctors/chatbot")
//@RequiredArgsConstructor
//public class DoctorChatbotController {
//
//    private final DoctorChatbotService doctorChatbotService;
//
//    /**
//     * POST /api/doctors/chatbot
//     *
//     * Process a chatbot query from doctor
//     *
//     * @param request ChatbotRequest containing:
//     *                - question (required): The question from doctor
//     *                - patientId (optional): Filter by patient ID
//     *                - recordId (optional): Filter by medical record ID
//     *                - appointmentId (optional): Filter by appointment ID
//     *
//     * @return ChatbotResponse containing:
//     *         - answer: Response from Gemini AI
//     *         - question: Original question
//     *         - success: Whether the request was successful
//     *         - errorMessage: Error message if failed (null if success)
//     *
//     * EXAMPLE REQUEST:
//     * {
//     *   "question": "What is the diagnosis for patient ID 123?",
//     *   "patientId": 123
//     * }
//     *
//     * EXAMPLE RESPONSE (Success):
//     * {
//     *   "answer": "Based on the medical records, patient ID 123 has been diagnosed with...",
//     *   "question": "What is the diagnosis for patient ID 123?",
//     *   "success": true,
//     *   "errorMessage": null
//     * }
//     *
//     * EXAMPLE RESPONSE (Error):
//     * {
//     *   "answer": null,
//     *   "question": "What is the diagnosis for patient ID 123?",
//     *   "success": false,
//     *   "errorMessage": "Failed to get response from AI"
//     * }
//     */
//    @PostMapping
//    public ResponseEntity<ChatbotResponse> askQuestion(@Valid @RequestBody ChatbotRequest request) {
//        // Gọi service để xử lý query
//        // Service sẽ:
//        // 1. Query database dựa trên filters
//        // 2. Build context
//        // 3. Gọi Gemini API
//        // 4. Trả về response
//        ChatbotResponse response = doctorChatbotService.processQuery(request);
//
//        // Trả về response với status code phù hợp
//        if (response.success()) {
//            return ResponseEntity.ok(response);
//        } else {
//            // Nếu có lỗi, vẫn trả về 200 nhưng success = false
//            // Hoặc có thể trả về 500 nếu muốn
//            return ResponseEntity.ok(response);
//        }
//    }
//}
//
