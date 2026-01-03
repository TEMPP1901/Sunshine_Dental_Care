package sunshine_dental_care.exceptions.hr;

/**
 * Exceptions cho Attendance module
 */
public class AttendanceExceptions {
    
    /**
     * Base exception cho tất cả lỗi liên quan attendance
     */
    public static class AttendanceException extends RuntimeException {
        public AttendanceException(String message) {
            super(message);
        }
        
        public AttendanceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception khi không tìm thấy attendance
     */
    public static class AttendanceNotFoundException extends AttendanceException {
        public AttendanceNotFoundException(Integer id) {
            super("Attendance not found with id: " + id);
        }
        
        public AttendanceNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception khi validation fail (face, WiFi, ...)
     */
    public static class AttendanceValidationException extends AttendanceException {
        public AttendanceValidationException(String message) {
            super(message);
        }
        
        public AttendanceValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception khi đã check-in rồi
     */
    public static class AlreadyCheckedInException extends AttendanceException {
        public AlreadyCheckedInException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception khi face verification fail
     */
    public static class FaceVerificationFailedException extends AttendanceException {
        public FaceVerificationFailedException(String message) {
            super(message);
        }
        
        public FaceVerificationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception khi WiFi validation fail
     */
    public static class WiFiValidationFailedException extends AttendanceException {
        public WiFiValidationFailedException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception khi không thể thực hiện operation
     */
    public static class AttendanceOperationException extends AttendanceException {
        public AttendanceOperationException(String operation, String reason) {
            super("Cannot " + operation + " attendance: " + reason);
        }
        
        public AttendanceOperationException(String message) {
            super(message);
        }
    }
}

