package sunshine_dental_care.exceptions.hr;

// Ngoại lệ cho các lỗi liên quan đến lịch phân công - rất quan trọng khi xử lý logic phân công/đặt lịch
public class ScheduleException extends RuntimeException {

    // Khởi tạo exception với message tiếng Anh
    public ScheduleException(String message) {
        super(message);
    }

    // Khởi tạo exception với message và nguyên nhân - dùng tracking lỗi phức tạp
    public ScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
