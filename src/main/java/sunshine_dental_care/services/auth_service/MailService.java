package sunshine_dental_care.services.auth_service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mail.javamail.JavaMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sunshine_dental_care.entities.EmailLog;
import sunshine_dental_care.entities.EmailTemplate;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.repositories.auth.EmailLogRepo;
import sunshine_dental_care.repositories.auth.EmailTemplateRepo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    private final EmailLogRepo emailLogRepo;
    private final EmailTemplateRepo templateRepo;

    private String render (String html, Map<String, String> vars) {
        String out = html;
        for (var e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    @Async
    @Transactional
    public void sendPatientCodeEmail(Patient patient, String locale) {
        String loc = (locale == null || locale.isBlank()) ? "en" : locale.toLowerCase();

        String subject,html;
        var tplOpt = templateRepo.findActiveByKeyAndLocale("PATIENT_CODE", loc);
        if(tplOpt.isPresent()) {
            EmailTemplate t = tplOpt.get();
            subject = t.getSubject();
            html = render(t.getHtmlBody(), Map.of(
                    "name", patient.getFullName(),
                    "patientCode", patient.getPatientCode()
            ));
        }else {
            if ("vi".equals(loc)) {
                subject = "Mã bệnh nhân của bạn – Sunshine Dental Care";
                html = """
                    <p>Xin chào %s,</p>
                    <p>Cảm ơn bạn đã đăng ký tài khoản tại Sunshine Dental Care.</p>
                    <p><b>Mã bệnh nhân (Patient Code) của bạn là: %s</b></p>
                    <p>Vui lòng lưu lại để tra cứu lịch sử khám trong tương lai.</p>
                    <p>Trân trọng,<br/>Sunshine Dental Care</p>
                """.formatted(patient.getFullName(), patient.getPatientCode());
            } else {
                subject = "[Sunshine Dental Care] Your Patient Code";
                html = """
                    <p>Hello %s,</p>
                    <p>Thank you for signing up with Sunshine Dental Care.</p>
                    <p><b>Your Patient Code is: %s</b></p>
                    <p>Please keep it safe for future history lookup.</p>
                    <p>Best regards,<br/>Sunshine Dental Care</p>
                """.formatted(patient.getFullName(), patient.getPatientCode());
            }
        }

        EmailLog log = new EmailLog();
        log.setPatient(patient);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);
        emailLogRepo.save(log);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setTo(patient.getEmail());
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);

            log.setStatus("SENT");
            log.setSentAt(Instant.now());
        } catch (Exception ex) {
            log.setStatus("FAILED");
            log.setErrorMessage(ex.getMessage());
        }
        emailLogRepo.save(log);
    }
}
