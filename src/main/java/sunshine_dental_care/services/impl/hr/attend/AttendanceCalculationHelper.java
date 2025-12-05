package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

@Component
public class AttendanceCalculationHelper {

    // Số phút đi muộn tối đa giới hạn (ví dụ: nếu đi muộn hơn 120 phút thì cũng chỉ tính 120)
    private static final int MAX_LATE_MINUTES_THRESHOLD = 120;

    // Tính số phút đi làm muộn
    public int calculateLateMinutes(LocalTime checkInTime, LocalTime expectedStartTime) {
        if (checkInTime.isAfter(expectedStartTime)) {
            long actualMinutesLate = Duration.between(expectedStartTime, checkInTime).toMinutes();
            // Nếu đi muộn quá ngưỡng thì chỉ tính tối đa
            return (int) (actualMinutesLate >= MAX_LATE_MINUTES_THRESHOLD ? MAX_LATE_MINUTES_THRESHOLD
                    : actualMinutesLate);
        }
        return 0;
    }

    // Tính số phút về sớm
    public int calculateEarlyMinutes(LocalTime checkOutTime, LocalTime expectedEndTime) {
        if (checkOutTime.isBefore(expectedEndTime)) {
            return (int) Duration.between(checkOutTime, expectedEndTime).toMinutes();
        }
        return 0;
    }

    // Tính tổng số giờ làm thực tế sau khi đã trừ thời gian nghỉ trưa
    public BigDecimal calculateActualWorkHours(Duration totalDuration, long lunchBreakMinutes) {
        long adjustedMinutes = totalDuration.toMinutes() - lunchBreakMinutes;
        if (adjustedMinutes < 0) {
            adjustedMinutes = 0;
        }
        // Làm tròn tới 2 chữ số phần thập phân
        return BigDecimal.valueOf(adjustedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    // Tính giờ làm lý thuyết (kỳ vọng) từ lúc bắt đầu đến lúc kết thúc ca làm
    public BigDecimal calculateExpectedWorkHours(LocalTime startTime, LocalTime endTime) {
        long expectedMinutes = Duration.between(startTime, endTime).toMinutes();
        return BigDecimal.valueOf(expectedMinutes).divide(BigDecimal.valueOf(60), 2,
                java.math.RoundingMode.HALF_UP);
    }
}
