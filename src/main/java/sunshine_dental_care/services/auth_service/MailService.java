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
    // 1. GỬI MÃ BỆNH NHÂN (PATIENT CODE)
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
    // 2. GỬI CẢNH BÁO KHÓA TÀI KHOẢN (ACCOUNT LOCKED)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAccountLockedEmail(User user) {
        String loc = "vi";

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

        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // 3. GỬI EMAIL RESET PASSWORD (ĐÂY LÀ PHẦN BẠN ĐANG THIẾU)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendResetPasswordEmail(User user, String token) {
        String loc = "vi";

        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("RESET_PASSWORD", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("RESET_PASSWORD");
                    nt.setLocale(loc);
                    nt.setSubject("Yêu cầu đặt lại mật khẩu - Sunshine Dental Care");
                    nt.setHtmlBody("<p>Xin chào {{name}},</p>"
                            + "<p>Bạn vừa yêu cầu đặt lại mật khẩu.</p>"
                            + "<p>Vui lòng nhấp vào link dưới đây (Hết hạn sau 15 phút):</p>"
                            + "<p><a href='{{link}}' style='font-weight:bold; font-size:16px;'>ĐẶT LẠI MẬT KHẨU</a></p>"
                            + "<p>Nếu không phải bạn yêu cầu, vui lòng bỏ qua email này.</p>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();

        // Link này trỏ về Frontend React (Port 5173)
        String resetLink = "http://localhost:5173/reset-password?token=" + token;

        String html = render(t.getHtmlBody(), Map.of(
                "name", user.getFullName(),
                "link", resetLink
        ));

        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // 4. GỬI EMAIL WELCOME (CHO OFFLINE / WALK-IN)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWelcomeEmail(Patient patient, String rawPassword) {
        String loc = "vi";

        // Tìm template hoặc tạo mới nếu chưa có
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("WELCOME_OFFLINE", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("WELCOME_OFFLINE");
                    nt.setLocale(loc);
                    nt.setSubject("Chào mừng bạn đến với Sunshine Dental Care");
                    nt.setHtmlBody("<div style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>"
                            + "<h2 style='color: #3366FF;'>Xin chào {{name}},</h2>"
                            + "<p>Hồ sơ bệnh nhân của bạn đã được tạo thành công tại quầy lễ tân.</p>"
                            + "<div style='background: #f4f6f8; padding: 15px; border-radius: 8px; margin: 20px 0;'>"
                            + "<p><b>Mã Bệnh Nhân:</b> <span style='color: #3366FF; font-size: 18px;'>{{patientCode}}</span></p>"
                            + "<p><b>Tài khoản đăng nhập:</b> {{username}}</p>"
                            + "<p><b>Mật khẩu mặc định:</b> {{password}}</p>"
                            + "</div>"
                            + "<p>Bạn có thể sử dụng thông tin trên để đăng nhập vào website và xem lịch sử khám, đặt lịch hẹn trực tuyến.</p>"
                            + "<p><i>Vui lòng đổi mật khẩu ngay sau khi đăng nhập lần đầu để bảo mật tài khoản.</i></p>"
                            + "<p>Trân trọng,<br/>Đội ngũ Sunshine Dental Care</p>"
                            + "</div>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of(
                "name", patient.getFullName(),
                "patientCode", patient.getPatientCode(),
                "username", patient.getPhone(), // Username là SĐT
                "password", rawPassword         // Password là SĐT (hoặc chuỗi bạn truyền vào)
        ));

        createAndSendLog(patient.getEmail(), subject, html, t, patient);
    }

    // =================================================================
    // HÀM HELPER CHUNG (LOGIC GỬI MAIL AN TOÀN)
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

    private void createAndSendLog(String toEmail, String subject, String htmlBody, EmailTemplate t, Patient patientLink) {
        EmailLog log = new EmailLog();

        if (patientLink != null) {
            log.setPatient(patientLink);
        }

        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);

        // 1. Lưu log lần đầu (Ignore lỗi nếu DB bắt buộc not null)
        try {
            emailLogRepo.save(log);
        } catch (Exception e) {
            System.err.println("Log Error (Step 1): " + e.getMessage());
        }

        // 2. Gửi Mail
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");

            h.setFrom(resolveFromAddress());
            h.setTo(toEmail);
            h.setSubject(subject);
            h.setText(htmlBody, true);

            mailSender.send(msg);

            log.setStatus("SENT");
            log.setSentAt(Instant.now());
        } catch (Exception ex) {
            log.setStatus("FAILED");
            log.setErrorMessage(ex.getMessage());
            System.err.println("Mail Send Error: " + ex.getMessage());
        }

        // 3. Update Log
        try {
            if (log.getId() != null) {
                emailLogRepo.save(log);
            }
        } catch (Exception e) {
            System.err.println("Log Error (Step 2): " + e.getMessage());
        }
    }

    private EmailTemplate createDefaultTemplate(String loc) {
        EmailTemplate nt = new EmailTemplate();
        nt.setKey("PATIENT_CODE");
        nt.setLocale(loc);
        if ("vi".equals(loc)) {
            nt.setSubject("Mã bệnh nhân của bạn – Sunshine Dental Care");
            nt.setHtmlBody("<p>Code: <b>{{patientCode}}</b></p>");
        } else {
            nt.setSubject("Sunshine Dental Care - Patient Code");
            nt.setHtmlBody("<p>Code: <b>{{patientCode}}</b></p>");
        }
        nt.setIsActive(true);
        return templateRepo.save(nt);
    }
}