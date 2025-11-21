package sunshine_dental_care.services.auth_service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.EmailLog;
import sunshine_dental_care.entities.EmailTemplate;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.auth.EmailLogRepo;
import sunshine_dental_care.repositories.auth.EmailTemplateRepo;
import sunshine_dental_care.repositories.auth.PatientRepo; // <--- Cần import PatientRepo

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

    // --- INJECT THÊM REPO NÀY ĐỂ TÌM PATIENT TỪ USER ---
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
    // HÀM 1: Dành cho SignUp (Đã có sẵn object Patient)
    // =================================================================
    @Async
    @Transactional
    public void sendPatientCodeEmail(Patient patient, String locale) {
        // Đã có patient, truyền trực tiếp vào
        sendEmailInternal(patient.getEmail(), patient.getFullName(), patient.getPatientCode(), locale, patient);
    }

    // =================================================================
    // HÀM 2: Dành cho Login Google (Chỉ có object User)
    // =================================================================
    @Async
    @Transactional
    public void sendPatientCodeEmail(User user, String locale) {
        // --- KHẮC PHỤC LỖI NULL PATIENT ID ---
        // Tìm Patient tương ứng với User này để ghi vào EmailLog
        Patient foundPatient = patientRepo.findByUserId(user.getId())
                .orElse(null);

        // Nếu tìm thấy patient thì truyền vào, nếu không thì đành chịu (nhưng thường là sẽ thấy)
        sendEmailInternal(user.getEmail(), user.getFullName(), user.getCode(), locale, foundPatient);
    }

    // =================================================================
    // HÀM XỬ LÝ CHUNG
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

        EmailLog log = new EmailLog();

        // --- QUAN TRỌNG: SET PATIENT ---
        // Lúc này patientLink đã được tìm thấy (từ hàm trên), nên sẽ không bị null nữa
        if (patientLink != null) {
            log.setPatient(patientLink);
        } else {
            // Trường hợp hiếm: Có User nhưng chưa kịp tạo Patient?
            // Nếu DB bắt buộc NOT NULL -> Sẽ lỗi tại đây.
            // Bạn nên đảm bảo logic tạo User luôn đi kèm tạo Patient.
            System.err.println("Warning: Sending email to user " + toEmail + " but no linked Patient found for log.");
        }

        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);

        // Lưu log lần 1
        emailLogRepo.save(log);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");

            h.setFrom(resolveFromAddress());
            h.setTo(toEmail);
            h.setSubject(subject);
            h.setText(html, true);

            mailSender.send(msg);

            log.setStatus("SENT");
            log.setSentAt(Instant.now());
        } catch (Exception ex) {
            log.setStatus("FAILED");
            log.setErrorMessage(ex.getMessage());
        }

        // Lưu log lần 2 (update status)
        emailLogRepo.save(log);
    }

    private EmailTemplate createDefaultTemplate(String loc) {
        EmailTemplate nt = new EmailTemplate();
        nt.setKey("PATIENT_CODE");
        nt.setLocale(loc);
        if ("vi".equals(loc)) {
            nt.setSubject("Mã bệnh nhân của bạn – Sunshine Dental Care");
            nt.setHtmlBody("<p>Xin chào {{name}},</p><p>Mã bệnh nhân của bạn là: <b>{{patientCode}}</b></p>");
        } else {
            nt.setSubject("[Sunshine Dental Care] Your Patient Code");
            nt.setHtmlBody("<p>Hello {{name}},</p><p>Your Patient Code is: <b>{{patientCode}}</b></p>");
        }
        nt.setIsActive(true);
        return templateRepo.save(nt);
    }
}