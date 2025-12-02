package sunshine_dental_care.exceptions.hr;

// Base exception for all HR Management related errors
public class HRManagementExceptions {

    // Exception cha cho tất cả lỗi liên quan HR Management
    public static class HRManagementException extends RuntimeException {
        public HRManagementException(String message) {
            super(message);
        }

        public HRManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Exception khi không tìm thấy Department theo id
    public static class DepartmentNotFoundException extends HRManagementException {
        public DepartmentNotFoundException(Integer id) {
            super("Department not found with id: " + id);
        }
        public DepartmentNotFoundException(String message) {
            super(message);
        }
    }

    // Exception khi không tìm thấy Clinic theo id
    public static class ClinicNotFoundException extends HRManagementException {
        public ClinicNotFoundException(Integer id) {
            super("Clinic not found with id: " + id);
        }
        public ClinicNotFoundException(String message) {
            super(message);
        }
    }

    // Exception khi không thể load dữ liệu từ database (tên entity và chi tiết)
    public static class DataLoadException extends HRManagementException {
        public DataLoadException(String entity, Throwable cause) {
            super("Failed to load " + entity + " from database", cause);
        }
        public DataLoadException(String message) {
            super(message);
        }
    }
}
