package sunshine_dental_care.exceptions.hr;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EmployeeExceptions {

    // Exception cha cho tất cả lỗi liên quan employee
    public static class EmployeeException extends RuntimeException {
        public EmployeeException(String message) {
            super(message);
        }

        public EmployeeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Exception khi không tìm thấy employee theo id
    public static class EmployeeNotFoundException extends EmployeeException {
        public EmployeeNotFoundException(Integer id) {
            super("Employee not found with id: " + id);
        }

        public EmployeeNotFoundException(String message) {
            super(message);
        }
    }

    // Exception validate dữ liệu employee hoặc constraint - rất quan trọng
    public static class EmployeeValidationException extends EmployeeException {

        public EmployeeValidationException(String message) {
            super(message);
        }

        public EmployeeValidationException(String message, Throwable cause) {
            super(message, cause);
        }

        // Xử lý riêng lỗi database constraint, trả về message dễ hiểu
        public EmployeeValidationException(org.springframework.dao.DataIntegrityViolationException ex) {
            super(parseDatabaseError(ex), ex);
        }

        // Phân tích chuỗi lỗi database trả về message rõ ràng nhất cho user - rất quan trọng
        private static String parseDatabaseError(org.springframework.dao.DataIntegrityViolationException ex) {
            Throwable root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause() : ex;
            String errorMessage = root.getMessage();

            if (errorMessage == null) {
                return "An error occurred while processing the request. Please try again.";
            }

            // Lỗi trùng số điện thoại
            if (errorMessage.contains("UQ_Users_phone_nn") || (errorMessage.contains("duplicate key") && errorMessage.contains("phone"))) {
                String phone = extractValueFromError(errorMessage, "duplicate key value is");
                if (phone != null) {
                    return "Phone number " + phone + " is already in use. Please choose a different phone number.";
                }
                return "Phone number is already in use. Please choose a different phone number.";
            }

            // Lỗi trùng email
            if (errorMessage.contains("UQ_Users_email") || (errorMessage.contains("duplicate key") && errorMessage.contains("email"))) {
                String email = extractValueFromError(errorMessage, "duplicate key value is");
                if (email != null) {
                    return "Email " + email + " is already in use. Please choose a different email.";
                }
                return "Email is already in use. Please choose a different email.";
            }

            // Lỗi trùng username
            if (errorMessage.contains("UQ_Users_username") || (errorMessage.contains("duplicate key") && errorMessage.contains("username"))) {
                String username = extractValueFromError(errorMessage, "duplicate key value is");
                if (username != null) {
                    return "Username " + username + " is already in use. Please choose a different username.";
                }
                return "Username is already in use. Please choose a different username.";
            }

            // Lỗi trùng generic
            if (errorMessage.contains("duplicate key")) {
                return "The information already exists in the system. Please check your email, phone number, or username.";
            }

            // Lỗi foreign key constraint
            if (errorMessage.contains("foreign key constraint") || errorMessage.contains("FK_")) {
                return "Invalid data. Please check the department, room, or clinic information.";
            }

            log.error("Unhandled database error: {}", errorMessage);
            return "An error occurred while processing the request. Please check your information and try again.";
        }

        // Hàm tách value (như email, sđt) từ error message để user biết giá trị trùng là gì - rất quan trọng
        private static String extractValueFromError(String errorMessage, String pattern) {
            try {
                int startIndex = errorMessage.indexOf(pattern);
                if (startIndex == -1) return null;

                int valueStart = errorMessage.indexOf("(", startIndex);
                if (valueStart == -1) return null;

                int valueEnd = errorMessage.indexOf(")", valueStart);
                if (valueEnd == -1) return null;

                return errorMessage.substring(valueStart + 1, valueEnd);
            } catch (Exception e) {
                log.warn("Failed to extract value from error message: {}", errorMessage);
                return null;
            }
        }
    }

    // Exception khi tạo mới mà employee đã tồn tại trùng khóa unique
    public static class EmployeeAlreadyExistsException extends EmployeeException {
        public EmployeeAlreadyExistsException(String field, String value) {
            super("Employee already exists with " + field + ": " + value);
        }

        public EmployeeAlreadyExistsException(String message) {
            super(message);
        }
    }

    // Exception không thể thực hiện thao tác nghiệp vụ như cập nhật/xóa đối tượng employee
    public static class EmployeeOperationException extends EmployeeException {
        public EmployeeOperationException(String operation, String reason) {
            super("Cannot " + operation + " employee: " + reason);
        }

        public EmployeeOperationException(String message) {
            super(message);
        }
    }
}
