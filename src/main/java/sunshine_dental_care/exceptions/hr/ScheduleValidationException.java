package sunshine_dental_care.exceptions.hr;

/*
  Exception khi validation lịch thất bại
 Tác dụng: Xử lý riêng cho các lỗi validation, có thể retry
 */
public class ScheduleValidationException extends ScheduleException {
    
    private final String validationErrors;
    
    public ScheduleValidationException(String validationErrors) {
        super("Schedule validation failed: " + validationErrors);
        this.validationErrors = validationErrors;
    }
    
    public String getValidationErrors() {
        return validationErrors;
    }
}
