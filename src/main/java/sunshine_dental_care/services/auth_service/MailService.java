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
    // HÀM 1: Gửi Mã Bệnh Nhân (Dành cho SignUp - Đã có Patient)
    // =================================================================
    @Async
    @Transactional
    public void sendPatientCodeEmail(Patient patient, String locale) {
        sendEmailInternal(patient.getEmail(), patient.getFullName(), patient.getPatientCode(), locale, patient);
    }

    // =================================================================
    // HÀM 2: Gửi Mã Bệnh Nhân (Dành cho Google Login - Chỉ có User)
    // =================================================================
    @Async
    @Transactional
    public void sendPatientCodeEmail(User user, String locale) {
        // Tìm Patient tương ứng để ghi Log
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        sendEmailInternal(user.getEmail(), user.getFullName(), user.getCode(), locale, foundPatient);
    }

    // =================================================================
    // HÀM 3: Gửi Cảnh Báo Khóa Tài Khoản (MỚI)
    // =================================================================
    @Async
    @Transactional
    public void sendAccountLockedEmail(User user) {
        String loc = "vi"; // Mặc định tiếng Việt hoặc lấy từ user preference

        // 1. Tìm hoặc tạo Template ACCOUNT_LOCKED
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("ACCOUNT_LOCKED", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("ACCOUNT_LOCKED");
                    nt.setLocale(loc);
                    if ("vi".equals(loc)) {
                        nt.setSubject("Cảnh báo bảo mật: Tài khoản của bạn đã bị khóa");
                        nt.setHtmlBody("<p>Xin chào {{name}},</p>"
                                + "<p>Hệ thống phát hiện có quá nhiều lần đăng nhập thất bại vào tài khoản của bạn.</p>"
                                + "<p>Để đảm bảo an toàn, <b>tài khoản của bạn đã bị khóa tạm thời.</b></p>"
                                + "<p>Vui lòng liên hệ với quản trị viên hoặc bộ phận CSKH để được mở khóa.</p>"
                                + "<p>Trân trọng,<br/>Sunshine Dental Care</p>");
                    } else {
                        nt.setSubject("Security Alert: Your account has been locked");
                        nt.setHtmlBody("<p>Hello {{name}},</p>"
                                + "<p>We detected too many failed login attempts on your account.</p>"
                                + "<p>For your security, <b>your account has been temporarily locked.</b></p>"
                                + "<p>Please contact the administrator to unlock your account.</p>"
                                + "<p>Regards,<br/>Sunshine Dental Care</p>");
                    }
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of(
                "name", user.getFullName()
        ));

        // 2. Tạo Log
        EmailLog log = new EmailLog();
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        if (foundPatient != null) {
            log.setPatient(foundPatient);
        } else {
            // Nếu là Admin/Staff không có Patient profile thì chấp nhận null (nếu DB cho phép)
            // Hoặc xử lý logic khác tùy DB
        }

        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);
        emailLogRepo.save(log);

        // 3. Gửi Mail
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");

            h.setFrom(resolveFromAddress());
            h.setTo(user.getEmail());
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

    // =================================================================
    // PRIVATE HELPER: Xử lý logic chung cho Patient Code
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

        if (patientLink != null) {
            log.setPatient(patientLink);
        } else {
            System.err.println("Warning: Sending email to " + toEmail + " but Patient is NULL. EmailLog might fail if patientId is NOT NULL in DB.");
        }

        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);

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