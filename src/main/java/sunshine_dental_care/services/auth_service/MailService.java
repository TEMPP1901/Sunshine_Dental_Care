package sunshine_dental_care.services.auth_service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.repositories.auth.EmailLogRepo;
import sunshine_dental_care.repositories.auth.EmailTemplateRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepo emailLogRepo;
    private final EmailTemplateRepo templateRepo;
    private final Environment env;
    private final PatientRepo patientRepo;

    // =================================================================
    // HELPER METHODS
    // =================================================================
    private String resolveFromAddress() {
        String fromProp = env.getProperty("app.mail.from");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        return "no-reply@sunshinedentalcare.vn";
    }

    private String render(String html, Map<String, String> vars) {
        if (html == null) return "";
        String out = html;
        for (var e : vars.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private String loadTemplateFromFile(String templatePath) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(templatePath);
            if (inputStream != null) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to load template from file: {} - {}", templatePath, e.getMessage());
        }
        return null;
    }

    // =================================================================
    // 1. AUTHENTICATION & SECURITY EMAILS
    // =================================================================

    // Gửi mã bệnh nhân (Welcome)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPatientCodeEmail(Patient patient, String locale) {
        sendEmailInternal(patient.getEmail(), patient.getFullName(), patient.getPatientCode(), locale, patient);
    }

    // Overload cho User
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPatientCodeEmail(User user, String locale) {
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        sendEmailInternal(user.getEmail(), user.getFullName(), user.getCode(), locale, foundPatient);
    }

    // Cảnh báo khóa tài khoản (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAccountLockedEmail(User user) {
        String loc = "vi"; // Mặc định hoặc lấy từ User Preference nếu có
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("ACCOUNT_LOCKED", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("ACCOUNT_LOCKED");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Cảnh báo bảo mật: Tài khoản đã bị tạm khóa");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Xin chào {{name}},</h2>"
                                + "<p>Hệ thống phát hiện nhập sai mật khẩu quá 5 lần.</p>"
                                + "<div style='background: #ffebee; padding: 15px; border-radius: 5px; color: #c62828;'>"
                                + "  <b>Tài khoản của bạn đã bị khóa tạm thời.</b>"
                                + "</div>"
                                + "<p>Vui lòng sử dụng tính năng <b>Quên mật khẩu</b> để mở khóa ngay lập tức.</p>"
                                + "</div>");
                    } else {
                        nt.setSubject("Security Alert: Account Temporarily Locked");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Hello {{name}},</h2>"
                                + "<p>We detected multiple failed login attempts.</p>"
                                + "<div style='background: #ffebee; padding: 15px; border-radius: 5px; color: #c62828;'>"
                                + "  <b>Your account has been temporarily locked.</b>"
                                + "</div>"
                                + "<p>Please use the <b>Forgot Password</b> feature to unlock your account immediately.</p>"
                                + "</div>");
                    }
                    return templateRepo.save(nt);
                });

        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName()));
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, foundPatient);
    }

    // Reset Password (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendResetPasswordEmail(User user, String token) {
        String loc = "vi"; // Mặc định
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("RESET_PASSWORD", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("RESET_PASSWORD");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Yêu cầu đặt lại mật khẩu");
                        nt.setHtmlBody("<div style='font-family: Arial;'>"
                                + "<p>Xin chào {{name}},</p>"
                                + "<p>Bạn vừa yêu cầu đặt lại mật khẩu. Vui lòng nhấn vào nút bên dưới:</p>"
                                + "<a href='{{link}}' style='background:#3366FF; color:white; padding:10px 20px; text-decoration:none; border-radius:5px;'>ĐẶT LẠI MẬT KHẨU</a>"
                                + "<p>Link hết hạn sau 15 phút.</p></div>");
                    } else {
                        nt.setSubject("Password Reset Request");
                        nt.setHtmlBody("<div style='font-family: Arial;'>"
                                + "<p>Hello {{name}},</p>"
                                + "<p>You requested a password reset. Please click the button below:</p>"
                                + "<a href='{{link}}' style='background:#3366FF; color:white; padding:10px 20px; text-decoration:none; border-radius:5px;'>RESET PASSWORD</a>"
                                + "<p>This link expires in 15 minutes.</p></div>");
                    }
                    return templateRepo.save(nt);
                });

        String resetLink = "http://localhost:5173/reset-password?token=" + token;
        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName(), "link", resetLink));
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, foundPatient);
    }

    // Verify Account (Updated Multi-language & Patient Code)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendVerificationEmail(User user, String token, String locale) {
        String loc = (locale == null || locale.isBlank()) ? "vi" : locale.toLowerCase();

        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("VERIFY_ACCOUNT", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("VERIFY_ACCOUNT");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Kích hoạt tài khoản & Mã bệnh nhân - Sunshine Dental Care");
                        nt.setHtmlBody("<div style='font-family: Arial;'>"
                                + "<p>Xin chào {{name}},</p>"
                                + "<p>Cảm ơn bạn đã đăng ký dịch vụ tại Sunshine Dental Care.</p>"
                                + "<div style='background: #e3f2fd; padding: 15px; border-radius: 5px; margin: 10px 0;'>"
                                + "   <p style='margin: 0;'>Mã bệnh nhân của bạn là: <b style='color: #0d47a1; font-size: 16px;'>{{patientCode}}</b></p>"
                                + "   <p style='margin: 5px 0 0; font-size: 13px; color: #555;'>Vui lòng lưu lại mã này để sử dụng khi đến phòng khám.</p>"
                                + "</div>"
                                + "<p>Vui lòng nhấn vào nút bên dưới để kích hoạt tài khoản:</p>"
                                + "<a href='{{link}}' style='background:#28a745; color:white; padding:10px 20px; text-decoration:none; border-radius:5px; display:inline-block;'>KÍCH HOẠT NGAY</a>"
                                + "</div>");
                    } else {
                        nt.setSubject("Account Activation & Patient Code - Sunshine Dental Care");
                        nt.setHtmlBody("<div style='font-family: Arial;'>"
                                + "<p>Hello {{name}},</p>"
                                + "<p>Thank you for registering with Sunshine Dental Care.</p>"
                                + "<div style='background: #e3f2fd; padding: 15px; border-radius: 5px; margin: 10px 0;'>"
                                + "   <p style='margin: 0;'>Your Patient Code is: <b style='color: #0d47a1; font-size: 16px;'>{{patientCode}}</b></p>"
                                + "   <p style='margin: 5px 0 0; font-size: 13px; color: #555;'>Please save this code for your visits.</p>"
                                + "</div>"
                                + "<p>Please click the button below to activate your account:</p>"
                                + "<a href='{{link}}' style='background:#28a745; color:white; padding:10px 20px; text-decoration:none; border-radius:5px; display:inline-block;'>ACTIVATE ACCOUNT</a>"
                                + "</div>");
                    }
                    return templateRepo.save(nt);
                });

        String verifyLink = "http://localhost:5173/verify-account?token=" + token;

        Map<String, String> vars = new HashMap<>();
        vars.put("name", user.getFullName());
        vars.put("link", verifyLink);
        vars.put("patientCode", user.getCode() != null ? user.getCode() : "Updating...");

        String html = render(t.getHtmlBody(), vars);
        Patient foundPatient = patientRepo.findByUserId(user.getId()).orElse(null);
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, foundPatient);
    }

    // =================================================================
    // 2. EMPLOYEE EMAILS (HR MODULE)
    // =================================================================
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWelcomeEmployeeEmail(User user, String password, String role, String department, String clinic, String locale) {
        String loc = (locale == null || locale.isBlank()) ? "vi" : locale.toLowerCase();
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("EMPLOYEE_WELCOME", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("EMPLOYEE_WELCOME");
                    nt.setLocale(loc);
                    nt.setSubject("Chào mừng nhân viên mới - Sunshine Dental Care");
                    String body = loadTemplateFromFile("templates/mail/welcome-employee-vi.html");
                    if (body == null) body = "<p>Chào mừng {{fullName}}. Username: {{username}}, Password: {{password}}</p>";
                    nt.setHtmlBody(body);
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        Map<String, String> vars = new HashMap<>();
        vars.put("fullName", user.getFullName());
        vars.put("email", user.getEmail());
        vars.put("username", user.getUsername());
        vars.put("password", password);
        vars.put("role", role);

        String html = render(t.getHtmlBody(), vars);
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEmployeeDeletionEmail(User user, String reason, String locale) {
        String loc = locale != null && !locale.isBlank() ? locale : "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("EMPLOYEE_DELETION", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("EMPLOYEE_DELETION");
                    nt.setLocale(loc);
                    nt.setSubject("Thông báo vô hiệu hóa tài khoản");
                    nt.setHtmlBody("<p>Chào {{fullName}}, tài khoản của bạn đã bị khóa. Lý do: <b>{{reason}}</b></p>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String html = render(t.getHtmlBody(), Map.of("fullName", user.getFullName(), "reason", reason));
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, null);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendDoctorResignationApprovalEmail(User user, String resignationDate, int remainingSchedules, String locale) {
        String loc = locale != null ? locale : "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("DOCTOR_RESIGNATION_APPROVAL", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("DOCTOR_RESIGNATION_APPROVAL");
                    nt.setLocale(loc);
                    nt.setSubject("Thông báo duyệt đơn nghỉ việc");
                    nt.setHtmlBody("<p>Chào {{fullName}}, đơn nghỉ việc ngày {{resignationDate}} đã được duyệt.</p>");
                    nt.setIsActive(true);
                    return templateRepo.save(nt);
                });

        String html = render(t.getHtmlBody(), Map.of("fullName", user.getFullName(), "resignationDate", resignationDate));
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, null);
    }

    // =================================================================
    // 3. BOOKING & PATIENT CARE EMAILS
    // =================================================================

    // Welcome Offline (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWelcomeEmail(Patient patient, String rawPassword) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("WELCOME_OFFLINE", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("WELCOME_OFFLINE");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Chào mừng bạn đến với Sunshine Dental Care");
                        nt.setHtmlBody("<div style='font-family: Arial;'>"
                                + "<h2>Xin chào {{name}},</h2>"
                                + "<p>Hồ sơ y tế điện tử của bạn đã được khởi tạo.</p>"
                                + "<p>Thông tin đăng nhập:</p>"
                                + "<ul><li>Email: (Email của bạn)</li><li>Mật khẩu: <b>{{password}}</b></li></ul>"
                                + "<p>Vui lòng đổi mật khẩu sau khi đăng nhập.</p></div>");
                    } else {
                        nt.setSubject("Welcome to Sunshine Dental Care");
                        nt.setHtmlBody("<div style='font-family: Arial;'>"
                                + "<h2>Hello {{name}},</h2>"
                                + "<p>Your electronic medical record has been created.</p>"
                                + "<p>Login Information:</p>"
                                + "<ul><li>Email: (Your Email)</li><li>Password: <b>{{password}}</b></li></ul>"
                                + "<p>Please change your password after logging in.</p></div>");
                    }
                    return templateRepo.save(nt);
                });
        String html = render(t.getHtmlBody(), Map.of("name", patient.getFullName(), "password", rawPassword));
        createAndSendLog(patient.getEmail(), t.getSubject(), html, t, patient);
    }

    // Xác nhận hủy lịch (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCancellationEmail(Patient patient, String appointmentId, String serviceName, String timeStr) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("APPOINTMENT_CANCELLED", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("APPOINTMENT_CANCELLED");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Xác nhận hủy lịch hẹn");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Xin chào {{name}},</h2>"
                                + "<p>Chúng tôi xác nhận lịch hẹn <b>#{{apptId}}</b> đã được hủy thành công.</p>"
                                + "<ul><li>Dịch vụ: {{service}}</li><li>Thời gian: {{time}}</li></ul>"
                                + "<p>Hẹn gặp lại bạn vào dịp khác.</p></div>");
                    } else {
                        nt.setSubject("Appointment Cancellation Confirmed");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Hello {{name}},</h2>"
                                + "<p>We confirm that appointment <b>#{{apptId}}</b> has been successfully cancelled.</p>"
                                + "<ul><li>Service: {{service}}</li><li>Time: {{time}}</li></ul>"
                                + "<p>We hope to see you again soon.</p></div>");
                    }
                    return templateRepo.save(nt);
                });

        String userName = (patient != null) ? patient.getFullName() : "Quý khách";
        String emailTo = (patient != null) ? patient.getEmail() : "";

        String html = render(t.getHtmlBody(), Map.of("name", userName, "apptId", appointmentId, "service", serviceName, "time", timeStr));
        createAndSendLog(emailTo, t.getSubject(), html, t, patient);
    }

    // Nhắc lịch 24h (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAppointmentReminderEmail(User user, Appointment appt, String timeStr, String serviceName, String address) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("APPOINTMENT_REMINDER", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("APPOINTMENT_REMINDER");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Nhắc nhở: Bạn có lịch hẹn vào ngày mai");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Xin chào {{name}},</h2>"
                                + "<p>Bạn có lịch hẹn nha khoa sắp tới.</p>"
                                + "<div style='background: #e3f2fd; padding: 15px; border-left: 4px solid #2196f3; border-radius: 4px;'>"
                                + "  <p><b>Thời gian:</b> {{time}}</p>"
                                + "  <p><b>Dịch vụ:</b> {{service}}</p>"
                                + "  <p><b>Địa chỉ:</b> {{address}}</p>"
                                + "</div>"
                                + "<p>Vui lòng đến đúng giờ để được phục vụ tốt nhất.</p></div>");
                    } else {
                        nt.setSubject("Reminder: You have an appointment tomorrow");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Hello {{name}},</h2>"
                                + "<p>You have an upcoming dental appointment.</p>"
                                + "<div style='background: #e3f2fd; padding: 15px; border-left: 4px solid #2196f3; border-radius: 4px;'>"
                                + "  <p><b>Time:</b> {{time}}</p>"
                                + "  <p><b>Service:</b> {{service}}</p>"
                                + "  <p><b>Address:</b> {{address}}</p>"
                                + "</div>"
                                + "<p>Please arrive on time for the best service.</p></div>");
                    }
                    return templateRepo.save(nt);
                });

        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName(), "time", timeStr, "service", serviceName, "address", address));
        Patient foundPatient = (appt.getPatient() != null) ? appt.getPatient() : null;
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, foundPatient);
    }

    // Đặt lịch thành công (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendBookingSuccessEmail(User user, Appointment appt, String timeStr, String serviceName, String address) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("BOOKING_SUCCESS", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("BOOKING_SUCCESS");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("Xác nhận: Đặt lịch thành công");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Cảm ơn {{name}},</h2>"
                                + "<p>Yêu cầu đặt lịch của bạn đã được ghi nhận.</p>"
                                + "<div style='background: #e8f5e9; padding: 15px; border-left: 4px solid #4caf50; border-radius: 4px;'>"
                                + "  <p><b>Thời gian:</b> {{time}}</p>"
                                + "  <p><b>Dịch vụ:</b> {{service}}</p>"
                                + "  <p><b>Địa chỉ:</b> {{address}}</p>"
                                + "</div>"
                                + "<p>Chúng tôi sẽ sớm liên hệ xác nhận.</p></div>");
                    } else {
                        nt.setSubject("Confirmed: Booking Successful");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Thank you {{name}},</h2>"
                                + "<p>Your booking request has been received.</p>"
                                + "<div style='background: #e8f5e9; padding: 15px; border-left: 4px solid #4caf50; border-radius: 4px;'>"
                                + "  <p><b>Time:</b> {{time}}</p>"
                                + "  <p><b>Service:</b> {{service}}</p>"
                                + "  <p><b>Address:</b> {{address}}</p>"
                                + "</div>"
                                + "<p>We will contact you shortly for confirmation.</p></div>");
                    }
                    return templateRepo.save(nt);
                });

        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName(), "time", timeStr, "service", serviceName, "address", address));
        Patient foundPatient = (appt.getPatient() != null) ? appt.getPatient() : null;
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, foundPatient);
    }

    // Nhắc lịch gấp (2h) (Updated Multi-language)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendUrgentReminderEmail(User user, Appointment appt, String timeStr, String serviceName, String address) {
        String loc = "vi";
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("URGENT_REMINDER", loc)
                .orElseGet(() -> {
                    EmailTemplate nt = new EmailTemplate();
                    nt.setKey("URGENT_REMINDER");
                    nt.setLocale(loc);
                    nt.setIsActive(true);

                    if ("vi".equals(loc)) {
                        nt.setSubject("⏰ Nhắc nhở: Lịch hẹn trong 2 giờ tới");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Xin chào {{name}},</h2>"
                                + "<p>Chỉ còn <b>2 giờ</b> nữa là đến lịch hẹn của bạn.</p>"
                                + "<div style='background: #fff3e0; padding: 15px; border-left: 4px solid #ff9800; border-radius: 4px;'>"
                                + "  <p><b>Thời gian:</b> {{time}}</p>"
                                + "  <p><b>Tại:</b> {{address}}</p>"
                                + "</div>"
                                + "<p>Vui lòng sắp xếp thời gian di chuyển nhé!</p></div>");
                    } else {
                        nt.setSubject("⏰ Reminder: Appointment in 2 hours");
                        nt.setHtmlBody("<div style='font-family: Arial; color: #333;'>"
                                + "<h2>Hello {{name}},</h2>"
                                + "<p>Only <b>2 hours</b> left until your appointment.</p>"
                                + "<div style='background: #fff3e0; padding: 15px; border-left: 4px solid #ff9800; border-radius: 4px;'>"
                                + "  <p><b>Time:</b> {{time}}</p>"
                                + "  <p><b>At:</b> {{address}}</p>"
                                + "</div>"
                                + "<p>Please arrange your travel time accordingly!</p></div>");
                    }
                    return templateRepo.save(nt);
                });

        String html = render(t.getHtmlBody(), Map.of("name", user.getFullName(), "time", timeStr, "service", serviceName, "address", address));
        Patient foundPatient = (appt.getPatient() != null) ? appt.getPatient() : null;
        createAndSendLog(user.getEmail(), t.getSubject(), html, t, foundPatient);
    }

    // =================================================================
    // CORE LOGGING & SENDING LOGIC
    // =================================================================
    private void sendEmailInternal(String toEmail, String fullName, String patientCode, String locale, Patient patientLink) {
        String loc = (locale == null || locale.isBlank()) ? "en" : locale.toLowerCase();
        EmailTemplate t = templateRepo.findActiveByKeyAndLocale("PATIENT_CODE", loc)
                .orElseGet(() -> createDefaultTemplate(loc));

        String html = render(t.getHtmlBody(), Map.of("name", fullName, "patientCode", patientCode == null ? "N/A" : patientCode));
        createAndSendLog(toEmail, t.getSubject(), html, t, patientLink);
    }

    private void createAndSendLog(String toEmail, String subject, String htmlBody, EmailTemplate t, Patient patientLink) {
        if (toEmail == null || toEmail.isEmpty()) {
            log.warn("Cannot send email: Recipient address is empty.");
            return;
        }

        EmailLog logEntry = new EmailLog();
        if (patientLink != null) logEntry.setPatient(patientLink);
        logEntry.setTemplate(t);
        logEntry.setStatus("QUEUED");
        logEntry.setQueuedAt(Instant.now());
        logEntry.setCost(BigDecimal.ZERO);

        try {
            emailLogRepo.save(logEntry);
        } catch (Exception ignored) {}

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(resolveFromAddress());
            h.setTo(toEmail);
            h.setSubject(subject);
            h.setText(htmlBody, true);
            mailSender.send(msg);

            logEntry.setStatus("SENT");
            logEntry.setSentAt(Instant.now());
        } catch (Exception ex) {
            log.error("Mail Send Error to {}: {}", toEmail, ex.getMessage());
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage(ex.getMessage());
        }

        try {
            if (logEntry.getId() != null) emailLogRepo.save(logEntry);
        } catch (Exception ignored) {}
    }

    private EmailTemplate createDefaultTemplate(String loc) {
        EmailTemplate nt = new EmailTemplate();
        nt.setKey("PATIENT_CODE");
        nt.setLocale(loc);
        if ("vi".equals(loc)) {
            nt.setSubject("Mã bệnh nhân của bạn – Sunshine Dental Care");
            nt.setHtmlBody("<p>Xin chào {{name}}, Mã bệnh nhân của bạn là: <b>{{patientCode}}</b></p>");
        } else {
            nt.setSubject("Sunshine Dental Care - Patient Code");
            nt.setHtmlBody("<p>Hello {{name}}, Your Patient Code is: <b>{{patientCode}}</b></p>");
        }
        nt.setIsActive(true);
        return templateRepo.save(nt);
    }
}