package sunshine_dental_care.exceptions.hr;

// Exception for schedule validation failures
public class ScheduleValidationException extends ScheduleException {
    
    private final String validationErrors;

    // Khởi tạo exception khi có lỗi validation lịch, truyền chuỗi lỗi chi tiết
    public ScheduleValidationException(String validationErrors) {
        super("Schedule validation failed: " + validationErrors);
        this.validationErrors = validationErrors;
    }

    // Lấy thông tin lỗi validation chi tiết trả về cho client hoặc frontend
    public String getValidationErrors() {
        return validationErrors;
    }
}
