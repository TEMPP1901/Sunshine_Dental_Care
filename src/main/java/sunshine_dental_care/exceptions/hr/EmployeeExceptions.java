package sunshine_dental_care.exceptions.hr;

/**
 * Tất cả exceptions liên quan đến Employee management
 * Gộp chung để dễ quản lý và sử dụng
 */
public class EmployeeExceptions {
    
    /**
     * Base exception cho tất cả employee errors
     */
    public static class EmployeeException extends RuntimeException {
        public EmployeeException(String message) {
            super(message);
        }
        
        public EmployeeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception khi không tìm thấy employee
     */
    public static class EmployeeNotFoundException extends EmployeeException {
        public EmployeeNotFoundException(Integer id) {
            super("Employee not found with id: " + id);
        }
        
        public EmployeeNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception khi validation employee thất bại
     */
    public static class EmployeeValidationException extends EmployeeException {
        public EmployeeValidationException(String message) {
            super(message);
        }
        
        public EmployeeValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception khi employee đã tồn tại (duplicate)
     */
    public static class EmployeeAlreadyExistsException extends EmployeeException {
        public EmployeeAlreadyExistsException(String field, String value) {
            super("Employee already exists with " + field + ": " + value);
        }
        
        public EmployeeAlreadyExistsException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception khi không thể thực hiện operation với employee
     */
    public static class EmployeeOperationException extends EmployeeException {
        public EmployeeOperationException(String operation, String reason) {
            super("Cannot " + operation + " employee: " + reason);
        }
        
        public EmployeeOperationException(String message) {
            super(message);
        }
    }
}
