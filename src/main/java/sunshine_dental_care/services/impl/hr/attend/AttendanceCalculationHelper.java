package sunshine_dental_care.services.impl.hr.attend;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import sunshine_dental_care.utils.WorkHoursConstants;

@Component
public class AttendanceCalculationHelper {

    // Giới hạn tối đa số phút đi muộn được tính
    private static final int MAX_LATE_MINUTES_THRESHOLD = 120;

    // Tính số phút đi muộn (chỉ tính tối đa tới ngưỡng cho phép)
    public int calculateLateMinutes(LocalTime checkInTime, LocalTime expectedStartTime) {
        if (checkInTime.isAfter(expectedStartTime)) {
            long actualMinutesLate = Duration.between(expectedStartTime, checkInTime).toMinutes();
            return (int) (actualMinutesLate >= MAX_LATE_MINUTES_THRESHOLD ? MAX_LATE_MINUTES_THRESHOLD : actualMinutesLate);
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

    // Tính tổng số giờ làm thực tế sau khi đã trừ thời gian nghỉ trưa (dùng cho mọi loại nhân viên)
    public BigDecimal calculateActualWorkHours(Duration totalDuration, long lunchBreakMinutes) {
        long adjustedMinutes = totalDuration.toMinutes() - lunchBreakMinutes;
        if (adjustedMinutes < 0) {
            adjustedMinutes = 0;
        }
        // Trả về giá trị làm tròn tới 2 số thập phân
        return BigDecimal.valueOf(adjustedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    // Tính số giờ làm kỳ vọng theo lý thuyết dựa vào giờ vào và ra mặc định
    public BigDecimal calculateExpectedWorkHours(LocalTime startTime, LocalTime endTime) {
        long expectedMinutes = Duration.between(startTime, endTime).toMinutes();
        return BigDecimal.valueOf(expectedMinutes)
                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP);
    }

    // Tính actualWorkHours có xét loại nhân viên (bác sĩ không trừ nghỉ trưa, còn lại áp dụng rule nghỉ trưa)
    public BigDecimal calculateActualWorkHours(Instant checkInTime, Instant checkOutTime, boolean isDoctor) {
        if (checkInTime == null || checkOutTime == null) {
            return BigDecimal.ZERO;
        }
        LocalTime checkInLocalTime = checkInTime.atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime();
        LocalTime checkOutLocalTime = checkOutTime.atZone(WorkHoursConstants.VN_TIMEZONE).toLocalTime();

        // Số phút làm việc thực tế (sau khi trừ nghỉ trưa nếu cần)
        Duration totalDuration = Duration.between(checkInTime, checkOutTime);

        int lunchBreakMinutes = WorkHoursConstants.calculateLunchBreakMinutes(checkInLocalTime, checkOutLocalTime, isDoctor);

        return calculateActualWorkHours(totalDuration, lunchBreakMinutes);
    }
}
