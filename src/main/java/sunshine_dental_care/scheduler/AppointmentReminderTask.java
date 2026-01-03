package sunshine_dental_care.scheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.notificationDTO.NotificationRequest;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.services.impl.notification.NotificationService;

/**
 * Scheduler task để gửi notification nhắc nhở lịch hẹn cho patient
 * - 24 giờ trước lịch hẹn
 * - 2 giờ trước lịch hẹn
 */
@Component
@RequiredArgsConstructor
public class AppointmentReminderTask {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderTask.class);

    private final AppointmentRepo appointmentRepo;
    private final NotificationService notificationService;

    // Chạy mỗi 30 phút để kiểm tra và gửi reminder
    @Scheduled(cron = "0 */30 * * * ?")
    @Transactional(readOnly = true)
    public void sendAppointmentReminders() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        log.debug("Starting appointment reminder check at {}", now);

        // Tính thời gian 24h và 2h từ bây giờ
        LocalDateTime reminder24h = now.plusHours(24);
        LocalDateTime reminder2h = now.plusHours(2);
        
        // Tìm các appointment cần gửi reminder 24h
        sendRemindersForTimeWindow(reminder24h, "24h");
        
        // Tìm các appointment cần gửi reminder 2h
        sendRemindersForTimeWindow(reminder2h, "2h");
    }

    /**
     * Gửi reminder cho các appointment trong khoảng thời gian cụ thể
     */
    private void sendRemindersForTimeWindow(LocalDateTime targetTime, String reminderType) {
        try {
            // Tính khoảng thời gian (30 phút trước và sau targetTime để có buffer)
            LocalDateTime windowStart = targetTime.minusMinutes(30);
            LocalDateTime windowEnd = targetTime.plusMinutes(30);
            
            Instant startInstant = windowStart.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            Instant endInstant = windowEnd.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

            // Tìm các appointment:
            // - Status là PENDING hoặc CONFIRMED
            // - StartDateTime trong khoảng thời gian này
            // - Có patient user (để gửi notification)
            List<Appointment> appointments = appointmentRepo.findByStatusInAndStartDateTimeBetween(
                    startInstant, endInstant);

            log.debug("Found {} appointments for {} reminder check", appointments.size(), reminderType);

            int sentCount = 0;
            int skippedCount = 0;

            for (Appointment appointment : appointments) {
                try {
                    // Kiểm tra xem đã gửi reminder cho loại này chưa
                    // (Có thể kiểm tra bằng cách tìm notification với type APPOINTMENT_REMINDER và message chứa reminderType)
                    // Tạm thời bỏ qua check này để đơn giản, có thể cải thiện sau

                    if (appointment.getPatient() == null || appointment.getPatient().getUser() == null) {
                        log.warn("Skipping appointment {} - no patient user", appointment.getId());
                        skippedCount++;
                        continue;
                    }

                    Integer patientUserId = appointment.getPatient().getUser().getId();
                    String clinicName = appointment.getClinic() != null ? appointment.getClinic().getClinicName() : "Phòng khám";
                    String doctorName = appointment.getDoctor() != null ? appointment.getDoctor().getFullName() : "Bác sĩ";
                    
                    // Format thời gian
                    LocalDateTime startDateTime = appointment.getStartDateTime()
                            .atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
                    String timeStr = startDateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));

                    String message;
                    if ("24h".equals(reminderType)) {
                        message = String.format(
                                "Nhắc nhở: Bạn có lịch hẹn tại %s với %s vào lúc %s (còn 24 giờ). Vui lòng đến đúng giờ.",
                                clinicName, doctorName, timeStr);
                    } else {
                        message = String.format(
                                "Nhắc nhở: Bạn có lịch hẹn tại %s với %s vào lúc %s (còn 2 giờ). Vui lòng chuẩn bị đến phòng khám.",
                                clinicName, doctorName, timeStr);
                    }

                    NotificationRequest notiRequest = NotificationRequest.builder()
                            .userId(patientUserId)
                            .type("APPOINTMENT_REMINDER")
                            .priority("MEDIUM")
                            .title("Nhắc nhở lịch hẹn")
                            .message(message)
                            .actionUrl("/appointments")
                            .relatedEntityType("APPOINTMENT")
                            .relatedEntityId(appointment.getId())
                            .build();

                    notificationService.sendNotification(notiRequest);
                    sentCount++;
                    log.debug("Sent {} reminder notification to patient {} for appointment {}", 
                            reminderType, patientUserId, appointment.getId());
                } catch (Exception e) {
                    log.error("Failed to send {} reminder for appointment {}: {}", 
                            reminderType, appointment.getId(), e.getMessage(), e);
                }
            }

            log.info("Appointment reminder ({}) completed: {} sent, {} skipped", 
                    reminderType, sentCount, skippedCount);
        } catch (Exception e) {
            log.error("Error sending appointment reminders ({}): {}", reminderType, e.getMessage(), e);
        }
    }
}
