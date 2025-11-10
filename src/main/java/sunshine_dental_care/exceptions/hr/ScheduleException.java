package sunshine_dental_care.exceptions.hr;

/*
 Exception cho các lỗi liên quan đến lịch phân công bác sĩ
 Tác dụng: Phân loại rõ ràng các lỗi HR, dễ debug và xử lý
 */
public class ScheduleException extends RuntimeException {
    
    public ScheduleException(String message) {
        super(message);
    }
    
    public ScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}
