package sunshine_dental_care.services.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import sunshine_dental_care.repositories.reception.AppointmentRepo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupService {
    private final AppointmentRepo appointmentRepo;

    @Scheduled(fixedRate = 60000) // Quét mỗi 1 phút

    public void scanAndCancelExpiredBookings() {

        // 1. Tính thời điểm giới hạn (Hiện tại - 15 phút)
        // Nghĩa là: Những đơn nào tạo TRƯỚC thời điểm này là đã quá hạn
        Instant expirationTime = Instant.now().minus(10, ChronoUnit.MINUTES);

        int count = appointmentRepo.cancelExpiredAppointments(
                "AWAITING_PAYMENT",
                "CANCELLED",
                expirationTime
        );

        if(count > 0) {
            log.info(" Auto-Cleanup: Đã hủy {} lịch hẹn treo quá hạn thanh toán.", count);
        }
    }
}
