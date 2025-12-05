package sunshine_dental_care.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.services.auth_service.MailService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppointmentReminderTask {

    private final AppointmentRepo appointmentRepo;
    private final MailService mailService;

    /**
     * PRODUCTION MODE:
     * Chạy định kỳ vào phút thứ 0 và phút thứ 30 của mỗi giờ.
     * Ví dụ: 8:00, 8:30, 9:00, 9:30...
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    @Transactional // Transaction giúp lấy dữ liệu Lazy Loading an toàn
    public void scanAndRemindAppointments() {
        // Instant now = Instant.now(); // Log này để debug nếu cần
        // System.out.println("--- [SCHEDULER] Bắt đầu quét nhắc hẹn: " + now);

        Instant now = Instant.now();

        // LOGIC QUÉT THỰC TẾ:
        // Chúng ta muốn nhắc khách trước 24h.
        // Để tránh bỏ sót, ta quét cửa sổ từ [23h tới] đến [24h30p tới].
        // Ví dụ: Bây giờ là 10:00 sáng.
        // -> WindowStart = 09:00 sáng mai.
        // -> WindowEnd   = 10:30 sáng mai.
        // Bất kỳ lịch hẹn nào rơi vào khoảng này sẽ được gửi mail.

        Instant windowStart = now.plus(23, ChronoUnit.HOURS);
        Instant windowEnd = now.plus(24, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);

        List<Appointment> appointments = appointmentRepo.findAppointmentsToRemind(windowStart, windowEnd);

        if (!appointments.isEmpty()) {
            System.out.println(">>> [REMINDER JOB] Tìm thấy " + appointments.size() + " lịch hẹn cần nhắc nhở.");
        }

        for (Appointment appt : appointments) {
            try {
                if (appt.getPatient() != null && appt.getPatient().getUser() != null) {

                    // 1. Format thời gian hiển thị (Giờ Việt Nam)
                    LocalDateTime ldt = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
                    String timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));

                    // 2. Lấy Tên Dịch Vụ (Lấy ngay trong Transaction này)
                    String serviceName = "Nha khoa tổng quát";
                    if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
                        AppointmentService as = appt.getAppointmentServices().get(0);
                        if (as.getService() != null) {
                            serviceName = as.getService().getServiceName();
                        }
                    }

                    // 3. Lấy Địa chỉ
                    String address = (appt.getClinic() != null) ? appt.getClinic().getAddress() : "Phòng khám chính";

                    // 4. Gửi mail (Async)
                    mailService.sendAppointmentReminderEmail(
                            appt.getPatient().getUser(),
                            appt,
                            timeStr,
                            serviceName,
                            address
                    );

                    // 5. Đánh dấu đã gửi để không quét lại lần sau
                    appt.setIsReminderSent(true);
                    appointmentRepo.save(appt);

                    System.out.println(">>> [SENT] Đã gửi nhắc nhở cho Appointment ID: " + appt.getId());
                }
            } catch (Exception e) {
                System.err.println(">>> [ERROR] Lỗi gửi nhắc nhở ID " + appt.getId() + ": " + e.getMessage());
                // Không throw exception để vòng lặp tiếp tục chạy cho các lịch hẹn khác
            }
        }
    }
}