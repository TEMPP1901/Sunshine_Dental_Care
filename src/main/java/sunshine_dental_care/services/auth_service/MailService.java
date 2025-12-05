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
import sunshine_dental_care.entities.*;
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

    // --- HELPER: Lấy email gửi đi ---
    private String resolveFromAddress() {
        String fromProp = env.getProperty("app.mail.from");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        return "no-reply@sunshinedentalcare.vn";
    }

    // --- HELPER: Thay thế biến vào HTML ---
    private String render(String html, Map<String, String> vars) {
        String out = html;
        for (var e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    // =================================================================
    // 1. GỬI MÃ BỆNH NHÂN (WELCOME)
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
    // 2. GỬI CẢNH BÁO KHÓA TÀI KHOẢN
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
                    nt.setSubject("Cảnh báo bảo mật: Tài khoản đã bị tạm khóa");
                    nt.setHtmlBody("<div style='font-family: Arial, sans-serif; line-height: 1.6; color: #333;'>"
                            + "<h2>Xin chào {{name}},</h2>"
                            + "<p>Hệ thống phát hiện nhập sai mật khẩu quá 5 lần liên tiếp.</p>"
                            + "<p>Để bảo vệ an toàn cho hồ sơ y tế của bạn, tài khoản đã bị <b>khóa tạm thời</b>.</p>"
                            + "<br/>"
                            + "<div style='background-color: #e8f0fe; padding: 20px; border-radius: 8px; border-left: 5px solid #3366FF;'>"
                            + "  <h3 style='margin-top: 0; color: #0D1B3E;'>Cách mở khóa nhanh nhất:</h3>"
                            + "  <p>Bạn không cần liên hệ quản trị viên. Hãy sử dụng tính năng <b>Quên mật khẩu</b> tại màn hình đăng nhập để thiết lập mật khẩu mới.</p>"
                            + "  <p>Sau khi đặt lại mật khẩu thành công, tài khoản sẽ <b>tự động mở khóa</b> ngay lập tức.</p>"
                            + "</div>"
                            + "<br/>"
                            + "<p>Trân trọng,<br/>Đội ngũ Sunshine Dental Care</p>"
                            + "</div>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName()));
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // 3. GỬI EMAIL RESET PASSWORD
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
                            + "<p><a href='{{link}}' style='font-weight:bold; font-size:16px;'>ĐẶT LẠI MẬT KHẨU</a></p>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String resetLink = "http://localhost:5173/reset-password?token=" + token;
        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName(), "link", resetLink));
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // 4. GỬI EMAIL KÍCH HOẠT TÀI KHOẢN (VERIFY)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendVerificationEmail(User user, String token) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("VERIFY_ACCOUNT", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("VERIFY_ACCOUNT");
                    nt.setLocale(loc);
                    nt.setSubject("Kích hoạt tài khoản - Sunshine Dental Care");
                    nt.setHtmlBody("<p>Xin chào {{name}},</p>"
                            + "<p>Cảm ơn bạn đã đăng ký tài khoản.</p>"
                            + "<p>Vui lòng nhấp vào link dưới đây để kích hoạt tài khoản (Hết hạn sau 24h):</p>"
                            + "<p><a href='{{link}}' style='background-color:#0d6efd; color:white; padding:10px 20px; text-decoration:none; border-radius:5px;'>KÍCH HOẠT NGAY</a></p>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String verifyLink = "http://localhost:5173/verify-account?token=" + token;
        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName(), "link", verifyLink));
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // 5. GỬI EMAIL XÁC NHẬN HỦY LỊCH
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCancellationEmail(String toEmail, String userName, String appointmentId, String serviceName, String timeStr) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("APPOINTMENT_CANCELLED", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("APPOINTMENT_CANCELLED");
                    nt.setLocale(loc);
                    nt.setSubject("Xác nhận hủy lịch hẹn - Sunshine Dental Care");
                    nt.setHtmlBody("<div style='font-family: Arial, sans-serif; color: #333;'>"
                            + "<h2>Xin chào {{name}},</h2>"
                            + "<p>Chúng tôi xác nhận bạn đã hủy thành công lịch hẹn khám nha khoa.</p>"
                            + "<ul>"
                            + "<li><b>Mã lịch hẹn:</b> #{{apptId}}</li>"
                            + "<li><b>Dịch vụ:</b> {{service}}</li>"
                            + "<li><b>Thời gian:</b> {{time}}</li>"
                            + "</ul>"
                            + "<p>Nếu bạn muốn đặt lại lịch mới, vui lòng truy cập website hoặc liên hệ hotline.</p>"
                            + "<p>Trân trọng,<br/>Sunshine Dental Care</p>"
                            + "</div>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of(
                "name", userName,
                "apptId", appointmentId,
                "service", serviceName,
                "time", timeStr
        ));

        Patient foundPatient = null;
        createAndSendLog(toEmail, subject, html, t, foundPatient);
    }

    // =================================================================
    // 6. GỬI EMAIL NHẮC HẸN (ĐÃ FIX LỖI ASYNC)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAppointmentReminderEmail(User user, Appointment appt, String timeStr, String serviceName, String address) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("APPOINTMENT_REMINDER", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("APPOINTMENT_REMINDER");
                    nt.setLocale(loc);
                    nt.setSubject("Nhắc nhở lịch hẹn sắp tới - Sunshine Dental Care");
                    nt.setHtmlBody("<div style='font-family: Arial, sans-serif; color: #333;'>"
                            + "<h2>Xin chào {{name}},</h2>"
                            + "<p>Bạn có một lịch hẹn nha khoa vào ngày mai.</p>"
                            + "<div style='background: #f4f4f4; padding: 15px; border-radius: 8px; border-left: 4px solid #3366FF;'>"
                            + "  <p style='margin: 5px 0;'><b>Thời gian:</b> {{time}}</p>"
                            + "  <p style='margin: 5px 0;'><b>Dịch vụ:</b> {{service}}</p>"
                            + "  <p style='margin: 5px 0;'><b>Địa chỉ:</b> {{address}}</p>"
                            + "</div>"
                            + "<p>Vui lòng đến đúng giờ để được phục vụ tốt nhất.</p>"
                            + "<p>Nếu cần thay đổi, vui lòng liên hệ hotline hoặc hủy trên website trước 24h.</p>"
                            + "<p>Trân trọng,<br/>Sunshine Dental Care</p>"
                            + "</div>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String subject = t.getSubject();

        // Sử dụng serviceName và address được truyền vào, KHÔNG TỰ QUERY LẠI DB TẠI ĐÂY
        String html = render(t.getHtmlBody(), Map.of(
                "name", user.getFullName(),
                "time", timeStr,
                "service", serviceName,
                "address", address
        ));

        Patient foundPatient = null;
        if(appt.getPatient() != null) foundPatient = appt.getPatient();

        createAndSendLog(user.getEmail(), subject, html, t, foundPatient);
    }

    // =================================================================
    // INTERNAL HELPERS
    // =================================================================
    private void sendEmailInternal(String toEmail, String fullName, String patientCode, String locale, Patient patientLink) {
        String loc = (locale == null || locale.isBlank()) ? "en" : locale.toLowerCase();
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("PATIENT_CODE", loc)
                .orElseGet(() -> createDefaultTemplate(loc));

        String subject = t.getSubject();
        String html = render(t.getHtmlBody(), Map.of("name", fullName, "patientCode", patientCode == null ? "N/A" : patientCode));
        createAndSendLog(toEmail, subject, html, t, patientLink);
    }

    private void createAndSendLog(String toEmail, String subject, String htmlBody, EmailTemplate t, Patient patientLink) {
        EmailLog log = new EmailLog();
        if (patientLink != null) log.setPatient(patientLink);
        log.setTemplate(t);
        log.setStatus("QUEUED");
        log.setQueuedAt(Instant.now());
        log.setCost(BigDecimal.ZERO);

        try { emailLogRepo.save(log); } catch (Exception ignored) {}

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

        try { if (log.getId() != null) emailLogRepo.save(log); } catch (Exception ignored) {}
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