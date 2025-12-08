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
     * PRODUCTION MODE: Chạy mỗi 30 phút.
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    @Transactional
    public void scanAndRemindAppointments() {
        Instant now = Instant.now();

        // --- LOGIC 1: NHẮC 24H (Giữ nguyên) ---
        Instant start24h = now.plus(23, ChronoUnit.HOURS);
        Instant end24h = now.plus(24, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
        List<Appointment> list24h = appointmentRepo.findAppointmentsToRemind(start24h, end24h);
        processList(list24h, "24H", false);

        // --- LOGIC 2: NHẮC GẤP (SỬA ĐỔI QUAN TRỌNG) ---
        // Thay đổi: Quét từ [Hiện tại] đến [2h30p tới]
        // Để bắt tất cả các lịch hẹn sắp diễn ra trong tương lai gần mà chưa gửi mail
        Instant startUrgent = now;
        Instant endUrgent = now.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);

        List<Appointment> listUrgent = appointmentRepo.findUrgentAppointmentsToRemind(startUrgent, endUrgent);
        processList(listUrgent, "URGENT", true);
    }

    private void processList(List<Appointment> appointments, String type, boolean isUrgent) {
        if (!appointments.isEmpty()) {
            System.out.println(">>> [SCAN " + type + "] Tìm thấy " + appointments.size() + " lịch.");
        }

        for (Appointment appt : appointments) {
            try {
                // Chỉ gửi nếu chưa quá hạn (Start time phải còn ở tương lai)
                // Vì nếu quét từ 'now', có thể dính các lịch vừa trôi qua vài giây
                if (appt.getStartDateTime().isBefore(Instant.now())) {
                    continue;
                }

                if (appt.getPatient() != null && appt.getPatient().getUser() != null) {
                    LocalDateTime ldt = LocalDateTime.ofInstant(appt.getStartDateTime(), ZoneId.systemDefault());
                    String timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy"));

                    String serviceName = "Nha khoa tổng quát";
                    if (appt.getAppointmentServices() != null && !appt.getAppointmentServices().isEmpty()) {
                        AppointmentService as = appt.getAppointmentServices().get(0);
                        if (as.getService() != null) serviceName = as.getService().getServiceName();
                    }

                    String address = (appt.getClinic() != null) ? appt.getClinic().getAddress() : "Phòng khám chính";

                    if (isUrgent) {
                        mailService.sendUrgentReminderEmail(appt.getPatient().getUser(), appt, timeStr, serviceName, address);
                        appt.setIsUrgentReminderSent(true);
                    } else {
                        mailService.sendAppointmentReminderEmail(appt.getPatient().getUser(), appt, timeStr, serviceName, address);
                        appt.setIsReminderSent(true);
                    }

                    appointmentRepo.save(appt);
                    System.out.println(">>> [SENT " + type + "] ID: " + appt.getId());
                }
            } catch (Exception e) {
                System.err.println(">>> [ERROR] ID " + appt.getId() + ": " + e.getMessage());
            }
        }
    }
}