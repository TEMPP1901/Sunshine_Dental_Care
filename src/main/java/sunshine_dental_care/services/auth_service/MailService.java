package sunshine_dental_care.services.auth_service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.EmailLog;
import sunshine_dental_care.entities.EmailTemplate;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.EmailLogRepo;
import sunshine_dental_care.repositories.auth.EmailTemplateRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepo emailLogRepo;
    private final EmailTemplateRepo templateRepo;
    private final Environment env;
    private final PatientRepo patientRepo;

    private String resolveFromAddress() {
        String fromProp = env.getProperty("app.mail.from");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        return "no-reply@sunshinedentalcare.vn";
    }

    private String render(String html, Map<String, String> vars) {
        String out = html;
        for (var e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    // =================================================================
    // 1. GỬI MÃ BỆNH NHÂN
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPatientCodeEmail(Patient patient, String locale) {
        sendEmailInternal(patient.getEmail(), patient.getFullName(), patient.getPatientCode(), locale, patient);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPatientCodeEmail(User user, String locale) {
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        sendEmailInternal(user.getEmail(), user.getFullName(), user.getCode(), locale, foundPatient);
    }

    // =================================================================
    // 2. GỬI CẢNH BÁO KHÓA TÀI KHOẢN (ĐÃ FIX LỖI)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW) // Transaction độc lập
    public void sendAccountLockedEmail(User user) {
        String loc = "vi";

        // 1. Tìm Template
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("ACCOUNT_LOCKED", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("ACCOUNT_LOCKED");
                    nt.setLocale(loc);
                    nt.setSubject("Cảnh báo bảo mật: Tài khoản của bạn đã bị khóa");
                    nt.setHtmlBody("<p>Xin chào {{name}},</p>"
                            + "<p>Hệ thống phát hiện nhập sai mật khẩu quá 5 lần.</p>"
                            + "<p>Tài khoản của bạn đã bị <b>khóa tạm thời</b>.</p>"
                            + "<p>Vui lòng liên hệ quản trị viên để mở khóa.</p>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName()));

        // 2. Tìm Patient (Có thể null nếu User chưa liên kết)
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);

        // 3. Gọi hàm chung để xử lý an toàn
        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // HÀM HELPER CHUNG
    // =================================================================
    private void sendEmailInternal(String toEmail, String fullName, String patientCode, String locale, Patient patientLink) {
        String loc = (locale == null || locale.isBlank()) ? "en" : locale.toLowerCase();
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("PATIENT_CODE", loc)
                .orElseGet(() -> createDefaultTemplate(loc));

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of(
                "name", fullName,
                "patientCode", patientCode == null ? "N/A" : patientCode
        ));

        createAndSendLog(toEmail, subject, html, t, patientLink);
    }

    // --- HÀM XỬ LÝ LOGIC GỬI MAIL AN TOÀN ---
    private void createAndSendLog(String toEmail, String subject, String htmlBody, EmailTemplate t, Patient patientLink) {
        EmailLog log = new EmailLog();

        // Nếu patientLink null, log.setPatient sẽ null.
        // Nếu DB bắt buộc patientId NOT NULL -> save() sẽ lỗi.
        if (patientLink != null) {
            log.setPatient(patientLink);
        }

        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);

        // BƯỚC QUAN TRỌNG: Dùng try-catch khi lưu Log lần 1
        // Để nếu lỗi DB (do NULL patientId) thì vẫn chạy tiếp xuống gửi mail
        try {
            emailLogRepo.save(log);
        } catch (Exception e) {
            System.err.println("Lỗi lưu EmailLog (Lần 1 - Bỏ qua để gửi mail): " + e.getMessage());
        }

        // BƯỚC GỬI MAIL
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");

            h.setFrom(resolveFromAddress());
            h.setTo(toEmail);
            h.setSubject(subject);
            h.setText(htmlBody, true);

            mailSender.send(msg);

            // Nếu gửi thành công, update trạng thái log
            log.setStatus("SENT");
            log.setSentAt(Instant.now());
        } catch (Exception ex) {
            log.setStatus("FAILED");
            log.setErrorMessage(ex.getMessage());
            System.err.println("Lỗi gửi mail thực tế: " + ex.getMessage());
        }

        // Lưu Log lần 2 (Update status)
        // Chỉ lưu nếu lần 1 đã có ID (tức là đã lưu thành công)
        if (log.getId() != null) {
            try {
                emailLogRepo.save(log);
            } catch (Exception e) {
                System.err.println("Lỗi cập nhật EmailLog (Lần 2): " + e.getMessage());
            }
        }
    }

    private EmailTemplate createDefaultTemplate(String loc) {
        EmailTemplate nt = new EmailTemplate();
        nt.setKey("PATIENT_CODE");
        nt.setLocale(loc);
        nt.setSubject("Sunshine Dental Care - Patient Code");
        nt.setHtmlBody("<p>Code: {{patientCode}}</p>");
        nt.setIsActive(true);
        return templateRepo.save(nt);
    }
}