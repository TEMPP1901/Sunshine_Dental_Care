package sunshine_dental_care.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.dto.aiDTO.AIChatRequest;
import sunshine_dental_care.dto.aiDTO.AIChatResponse;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.doctor.DoctorRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.services.ai.client.DentalAIClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIChatService {

    private final DentalAIClient dentalAIClient;
    private final ServiceVariantRepo serviceVariantRepo;
    private final DoctorRepo doctorRepo;
    private final AppointmentRepo appointmentRepo;
    private final ClinicRepo clinicRepo; // Repo lấy cơ sở
    private final ObjectMapper objectMapper;

    private PromptConfig promptConfig;

    @PostConstruct
    public void initPromptConfig() {
        try {
            ClassPathResource resource = new ClassPathResource("ai-prompt-config.json");
            this.promptConfig = objectMapper.readValue(resource.getInputStream(), PromptConfig.class);
        } catch (Exception e) {
            e.printStackTrace();
            this.promptConfig = new PromptConfig(); // Fallback
        }
    }

    @Transactional(readOnly = true)
    public AIChatResponse processChat(String userMessage, List<AIChatRequest.MessageHistory> history) {

        // 1. Lấy Dịch vụ Active
        List<ServiceVariant> allServices = serviceVariantRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .collect(Collectors.toList());

        // 2. Lấy Cơ sở Active (Fix lỗi tham số thừa ở đây)
        List<Clinic> allClinics = clinicRepo.findByIsActiveTrue();

        // 3. Lấy Bác sĩ
        List<User> allDoctors = doctorRepo.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> u.getDoctorSpecialties() != null && !u.getDoctorSpecialties().isEmpty())
                .filter(u -> u.getDoctorSpecialties().stream().anyMatch(DoctorSpecialty::getIsActive))
                .collect(Collectors.toList());

        // 4. Tính độ Hot
        Map<Integer, Long> doctorBookingCounts = new HashMap<>();
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Appointment> recentAppointments = appointmentRepo.findAll().stream()
                .filter(a -> a.getStartDateTime().isAfter(thirtyDaysAgo))
                .toList();

        for (User doc : allDoctors) {
            long count = recentAppointments.stream()
                    .filter(a -> a.getDoctor() != null && a.getDoctor().getId().equals(doc.getId()))
                    .count();
            doctorBookingCounts.put(doc.getId(), count);
        }

        allDoctors.sort((d1, d2) -> Long.compare(doctorBookingCounts.getOrDefault(d2.getId(), 0L), doctorBookingCounts.getOrDefault(d1.getId(), 0L)));

        // 5. Build Prompt (GỌI VỚI 4 THAM SỐ)
        String systemPrompt = buildSystemPrompt(allServices, allDoctors, allClinics, doctorBookingCounts);

        // 6. Xử lý chat như cũ
        StringBuilder conversationBuilder = new StringBuilder();
        conversationBuilder.append(systemPrompt).append("\n\n=== BẮT ĐẦU HỘI THOẠI ===\n");
        if (history != null) {
            int start = Math.max(0, history.size() - 4);
            for (int i = start; i < history.size(); i++) {
                AIChatRequest.MessageHistory msg = history.get(i);
                conversationBuilder.append(msg.getRole().equals("user") ? "Khách: " : "AI: ")
                        .append(msg.getContent()).append("\n");
            }
        }
        conversationBuilder.append("Khách: ").append(userMessage).append("\nAI (JSON ONLY):");

        try {
            String rawResponse = dentalAIClient.generateContent(conversationBuilder.toString());
            String jsonResponse = cleanJson(rawResponse);
            JsonNode root = objectMapper.readTree(jsonResponse);

            String replyText = root.path("reply").asText();

            List<AIChatResponse.ServiceSuggestion> suggestedServices = new ArrayList<>();
            if (root.has("suggested_services_ids")) {
                for (JsonNode idNode : root.get("suggested_services_ids")) {
                    Integer sId = idNode.asInt();
                    allServices.stream().filter(s -> s.getId().equals(sId)).findFirst().ifPresent(s -> {
                        suggestedServices.add(AIChatResponse.ServiceSuggestion.builder()
                                .id(s.getId())
                                .name(s.getService().getServiceName() + " (" + s.getVariantName() + ")")
                                .price(s.getPrice())
                                .duration(s.getDuration() + " phút")
                                .build());
                    });
                }
            }

            List<AIChatResponse.DoctorSuggestion> suggestedDoctors = new ArrayList<>();
            if (root.has("suggested_doctor_ids")) {
                for (JsonNode idNode : root.get("suggested_doctor_ids")) {
                    Integer dId = idNode.asInt();
                    allDoctors.stream().filter(d -> d.getId().equals(dId)).findFirst().ifPresent(d -> {
                        String specList = d.getDoctorSpecialties().stream()
                                .filter(DoctorSpecialty::getIsActive)
                                .map(DoctorSpecialty::getSpecialtyName)
                                .collect(Collectors.joining(", "));

                        suggestedDoctors.add(AIChatResponse.DoctorSuggestion.builder()
                                .id(d.getId())
                                .fullName(d.getFullName())
                                .specialty(specList)
                                .avatarUrl(d.getAvatarUrl())
                                .build());
                    });
                }
            }

            return AIChatResponse.builder()
                    .replyText(replyText)
                    .suggestedServices(suggestedServices)
                    .suggestedDoctors(suggestedDoctors)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return AIChatResponse.builder().replyText("Hệ thống đang bận, vui lòng thử lại sau.").build();
        }
    }

    // ĐÂY LÀ HÀM CẦN SỬA: Thêm tham số List<Clinic> clinics vào
    private String buildSystemPrompt(List<ServiceVariant> services, List<User> doctors, List<Clinic> clinics, Map<Integer, Long> counts) {
        StringBuilder sb = new StringBuilder();

        if (promptConfig != null) {
            sb.append(promptConfig.getRole_definition()).append("\n");

            sb.append("\nDANH SÁCH CƠ SỞ:\n");
            // Ưu tiên dùng dữ liệu DB nếu có
            if (!clinics.isEmpty()) {
                for (Clinic c : clinics) {
                    sb.append("- ").append(c.getClinicName()).append(": ").append(c.getAddress()).append("\n");
                }
            } else if (promptConfig.getClinic_info() != null && promptConfig.getClinic_info().getBranches() != null) {
                // Fallback về JSON nếu DB rỗng
                for (PromptConfig.Branch b : promptConfig.getClinic_info().getBranches()) {
                    sb.append("- ").append(b.getName()).append(": ").append(b.getAddress()).append("\n");
                }
            }

            if (promptConfig.getClinic_info() != null) {
                sb.append("Giờ làm việc: ").append(promptConfig.getClinic_info().getHours()).append("\n");
            }

            sb.append("\nLUẬT (BẮT BUỘC):\n");
            if (promptConfig.getRules() != null) {
                promptConfig.getRules().forEach(r -> sb.append("- ").append(r).append("\n"));
            }

            if (promptConfig.getTraining_examples() != null) {
                sb.append("\nVÍ DỤ:\n");
                promptConfig.getTraining_examples().forEach(ex ->
                        sb.append("U: ").append(ex.getUser()).append(" -> AI: ").append(ex.getAi_reply()).append("\n")
                );
            }
        }

        sb.append("\n--- DỮ LIỆU THỰC TẾ ---\n");
        sb.append("1. DỊCH VỤ:\n");
        services.stream().limit(30).forEach(s ->
                sb.append(String.format("- [%d] %s (%s) | %s | %s VND\n",
                        s.getId(), s.getService().getServiceName(), s.getVariantName(),
                        s.getService().getCategory(), s.getPrice()))
        );

        sb.append("\n2. BÁC SĨ (CHỈ DÙNG CHO VIP):\n");
        if (doctors.isEmpty()) {
            sb.append("(Hiện tại chưa có bác sĩ nào có chuyên khoa)\n");
        } else {
            doctors.stream().limit(10).forEach(d -> {
                String specs = d.getDoctorSpecialties().stream()
                        .filter(DoctorSpecialty::getIsActive)
                        .map(DoctorSpecialty::getSpecialtyName)
                        .collect(Collectors.joining(", "));

                sb.append(String.format("- ID: %d | Bs. %s | Chuyên: [%s] | Hot: %d/10\n",
                        d.getId(), d.getFullName(), specs,
                        Math.min(10, counts.getOrDefault(d.getId(), 0L) + 1)));
            });
        }

        sb.append("\nFORMAT JSON: { \"reply\": \"...\", \"suggested_services_ids\": [], \"suggested_doctor_ids\": [] }");
        return sb.toString();
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }
}