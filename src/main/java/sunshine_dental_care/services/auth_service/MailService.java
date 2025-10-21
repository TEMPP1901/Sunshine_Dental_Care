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
    private final Environment env;

    private String resolveFromAddress() {
        String fromProp = env.getProperty("app.mail.from");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        String mailUser = env.getProperty("spring.mail.username");
        if (mailUser != null && !mailUser.isBlank()) {
            return mailUser;
        }
        return "no-reply@sunshinedentalcare.vn";
    }


    private String render(String html, Map<String, String> vars) {
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

        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("PATIENT_CODE", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("PATIENT_CODE");
                    nt.setLocale(loc);
                    if ("vi".equals(loc)) {
                        nt.setSubject("Mã bệnh nhân của bạn – Sunshine Dental Care");
                        nt.setHtmlBody("<p>Xin chào {{name}},</p>"
                                + "<p>Cảm ơn bạn đã đăng ký tài khoản tại Sunshine Dental Care.</p>"
                                + "<p><b>Mã bệnh nhân (Patient Code) của bạn là: {{patientCode}}</b></p>"
                                + "<p>Vui lòng lưu lại để tra cứu lịch sử khám trong tương lai.</p>"
                                + "<p>Trân trọng,<br/>Sunshine Dental Care</p>");
                    } else {
                        nt.setSubject("[Sunshine Dental Care] Your Patient Code");
                        nt.setHtmlBody("<p>Hello {{name}},</p>"
                                + "<p>Thank you for signing up with Sunshine Dental Care.</p>"
                                + "<p><b>Your Patient Code is: {{patientCode}}</b></p>"
                                + "<p>Please keep it safe for future history lookup.</p>"
                                + "<p>Best regards,<br/>Sunshine Dental Care</p>");
                    }
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of(
                "name", patient.getFullName(),
                "patientCode", patient.getPatientCode()
        ));

        EmailLog log = new EmailLog();
        log.setPatient(patient);
        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);
        emailLogRepo.save(log);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");

            String fromAddr = resolveFromAddress();
            h.setFrom(fromAddr);
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
