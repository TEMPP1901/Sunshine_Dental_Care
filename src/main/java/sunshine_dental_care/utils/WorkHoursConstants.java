package sunshine_dental_care.utils;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

// Các hằng số và hàm hỗ trợ liên quan đến giờ ca làm việc
public final class WorkHoursConstants {
    private WorkHoursConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Giờ bắt đầu và kết thúc nghỉ trưa
    public static final LocalTime LUNCH_BREAK_START = LocalTime.of(11, 0);
    public static final LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
    public static final int LUNCH_BREAK_MINUTES = 120; // Số phút nghỉ trưa

    // Giờ bắt đầu, kết thúc các ca
    public static final LocalTime MORNING_SHIFT_START = LocalTime.of(8, 0);
    public static final LocalTime MORNING_SHIFT_END = LocalTime.of(11, 0);

    public static final LocalTime AFTERNOON_SHIFT_START = LocalTime.of(13, 0);
    public static final LocalTime AFTERNOON_SHIFT_END = LocalTime.of(18, 0);

    // Giờ mặc định cho nhân viên làm cả ngày
    public static final LocalTime EMPLOYEE_START_TIME = LocalTime.of(8, 0);
    public static final LocalTime EMPLOYEE_END_TIME = LocalTime.of(18, 0);
    public static final int EMPLOYEE_EXPECTED_HOURS = 8;

    // Loại ca làm việc
    public static final String SHIFT_TYPE_MORNING = "MORNING";
    public static final String SHIFT_TYPE_AFTERNOON = "AFTERNOON";
    public static final String SHIFT_TYPE_FULL_DAY = "FULL_DAY";

    // Xác định ca làm việc dựa vào thời gian bắt đầu
    public static String determineShiftType(LocalTime startTime) {
        if (startTime == null) {
            return SHIFT_TYPE_FULL_DAY;
        }
        // Trước 11h: ca sáng
        if (startTime.isBefore(LUNCH_BREAK_START)) {
            return SHIFT_TYPE_MORNING;
        }
        // 11h - 18h: ca chiều
        if (startTime.isBefore(AFTERNOON_SHIFT_END) || startTime.equals(AFTERNOON_SHIFT_END)) {
            return SHIFT_TYPE_AFTERNOON;
        }
        // Sau 18h: full day
        return SHIFT_TYPE_FULL_DAY;
    }

    // Xác định ca cho bác sĩ ở thời điểm hiện tại (dùng khi check-in bác sĩ)
    public static String determineShiftForDoctor(LocalTime currentTime) {
        if (currentTime == null) {
            return SHIFT_TYPE_FULL_DAY;
        }
        if (currentTime.isBefore(LUNCH_BREAK_START)) {
            return SHIFT_TYPE_MORNING;
        }
        if (currentTime.isBefore(AFTERNOON_SHIFT_END)) {
            return SHIFT_TYPE_AFTERNOON;
        }
        return SHIFT_TYPE_FULL_DAY;
    }

    // Tính số phút trừ nghỉ trưa, chỉ trừ cho nhân viên checkin trước 11h và checkout sau 13h
    public static int calculateLunchBreakMinutes(LocalTime checkInTime, LocalTime checkOutTime, boolean isDoctor) {
        // Bác sĩ không trừ nghỉ trưa
        if (isDoctor) {
            return 0;
        }
        // Nhân viên: check-in trước 11h và check-out sau 13h mới trừ
        if (checkInTime != null && checkOutTime != null
                && checkInTime.isBefore(LUNCH_BREAK_START)
                && checkOutTime.isAfter(LUNCH_BREAK_END)) {
            return LUNCH_BREAK_MINUTES;
        }
        return 0;
    }

    // Tính số phút nghỉ trưa từ Instant, chuyển thành LocalTime rồi xử lý như trên
    public static int calculateLunchBreakMinutes(Instant checkInTime, Instant checkOutTime, boolean isDoctor) {
        if (checkInTime == null || checkOutTime == null) {
            return 0;
        }
        LocalTime checkInLocalTime = checkInTime.atZone(ZoneId.systemDefault()).toLocalTime();
        LocalTime checkOutLocalTime = checkOutTime.atZone(ZoneId.systemDefault()).toLocalTime();
        return calculateLunchBreakMinutes(checkInLocalTime, checkOutLocalTime, isDoctor);
    }

    // Kiểm tra ca làm việc có khớp với thời gian bắt đầu không (dùng cho lọc tìm lịch phù hợp)
    public static boolean matchesShiftType(LocalTime startTime, String shiftType) {
        if (startTime == null || shiftType == null) {
            return false;
        }
        if (SHIFT_TYPE_MORNING.equals(shiftType)) {
            // Ca sáng tính cả mốc 11h (ca sáng kết thúc)
            return startTime.isBefore(LUNCH_BREAK_START) || startTime.equals(LUNCH_BREAK_START);
        }
        if (SHIFT_TYPE_AFTERNOON.equals(shiftType)) {
            // Ca chiều: bắt đầu từ 11h trở đi đến hết 18h
            return (startTime.isAfter(LUNCH_BREAK_START) || startTime.equals(LUNCH_BREAK_START))
                    && (startTime.isBefore(AFTERNOON_SHIFT_END) || startTime.equals(AFTERNOON_SHIFT_END));
        }
        // FULL_DAY: mọi thời gian đều khớp
        return true;
    }
}
