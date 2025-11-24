package sunshine_dental_care.exceptions;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.exceptions.auth.DuplicateEmailException;
import sunshine_dental_care.exceptions.auth.DuplicateUsernameException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AlreadyCheckedInException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceNotFoundException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.AttendanceValidationException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.FaceVerificationFailedException;
import sunshine_dental_care.exceptions.hr.AttendanceExceptions.WiFiValidationFailedException;
import sunshine_dental_care.exceptions.auth.DuplicateEmailException;
import sunshine_dental_care.exceptions.auth.DuplicateUsernameException;
import sunshine_dental_care.exceptions.hr.DoctorNotAvailableException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeNotFoundException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeValidationException;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.DataLoadException;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.DepartmentNotFoundException;
import sunshine_dental_care.exceptions.hr.HRManagementExceptions.HRManagementException;
import sunshine_dental_care.exceptions.hr.ScheduleException;
import sunshine_dental_care.exceptions.hr.ScheduleValidationException;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Xử lý lỗi validation dữ liệu đầu vào (request body @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("message", "Request validation failed");
        response.put("errors", errors);

        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    // Xử lý validate lịch (Schedule Validation Exception, trả về lỗi chi tiết lịch không phù hợp)
    @ExceptionHandler(ScheduleValidationException.class)
    public ResponseEntity<Map<String, Object>> handleScheduleValidationException(ScheduleValidationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Schedule Validation Failed");
        response.put("message", ex.getMessage());
        response.put("validationErrors", ex.getValidationErrors());

        log.warn("Schedule validation failed: {}", ex.getValidationErrors());
        return ResponseEntity.badRequest().body(response);
    }

    // Xử lý trường hợp bác sĩ bị trùng lịch hoặc đã bận (Doctor Not Available)
    @ExceptionHandler(DoctorNotAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleDoctorNotAvailableException(DoctorNotAvailableException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Doctor Not Available");
        response.put("message", ex.getMessage());
        response.put("doctorId", ex.getDoctorId());
        response.put("workDate", ex.getWorkDate());

        log.warn("Doctor not available: Doctor ID {} on {}", ex.getDoctorId(), ex.getWorkDate());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Các lỗi phân công lịch khác
    @ExceptionHandler(ScheduleException.class)
    public ResponseEntity<Map<String, Object>> handleScheduleException(ScheduleException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Schedule Error");
        response.put("message", ex.getMessage());

        log.error("Schedule error: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(response);
    }

//    @ExceptionHandler(BadCredentialsException.class)
//    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(BadCredentialsException ex) {
//        Map<String, Object> response = new HashMap<>();
//        response.put("timestamp", LocalDateTime.now());
//        response.put("status", HttpStatus.UNAUTHORIZED.value());
//        response.put("error", "Authentication Failed");
//        response.put("message", ex.getMessage());
//
//        log.warn("Bad credentials: {}", ex.getMessage());
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(response);
//    }

    // Xử lý trường hợp không có quyền
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", "Access Denied");
        response.put("message", "You do not have permission to access this resource");

        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    // Xử lý lỗi xác thực (chung jwt/basic auth)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", "Authentication Failed");
        response.put("message", ex.getMessage() != null ? ex.getMessage() : "Authentication failed");

        log.warn("Authentication error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /*
     Xử lý IllegalArgumentException
     Tác dụng: Xử lý các lỗi argument không hợp lệ
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Invalid Argument");
        response.put("message", ex.getMessage());

        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    /*
     Xử lý IllegalStateException
     Tác dụng: Xử lý các lỗi state không hợp lệ (ví dụ: missing role, missing patient sequence)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Configuration Error");
        response.put("message", ex.getMessage());

        log.error("Illegal state: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /*
     Xử lý Employee Not Found Exception
     Tác dụng: Trả về lỗi 404 khi không tìm thấy nhân viên
     */
    // Xử lý lỗi không tìm thấy nhân viên
    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeeNotFoundException(EmployeeNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Employee Not Found");
        response.put("message", ex.getMessage());

        log.warn("Employee not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Xử lý lỗi validate dữ liệu nhân viên (ví dụ trùng số ĐT/email)
    @ExceptionHandler(EmployeeValidationException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeeValidationException(EmployeeValidationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Employee Validation Failed");
        response.put("message", ex.getMessage());

        log.warn("Employee validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    // Xử lý lỗi logic khác của Employee
    @ExceptionHandler(EmployeeException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeeException(EmployeeException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Employee Error");
        response.put("message", ex.getMessage());

        log.error("Employee error: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(response);
    }

    // Lỗi không đọc được dữ liệu database (data load fail)
    @ExceptionHandler(DataLoadException.class)
    public ResponseEntity<Map<String, Object>> handleDataLoadException(DataLoadException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Data Load Failed");
        response.put("message", ex.getMessage());

        log.error("Data load error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // Lỗi không tìm thấy phòng ban
    @ExceptionHandler(DepartmentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDepartmentNotFoundException(DepartmentNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Department Not Found");
        response.put("message", ex.getMessage());

        log.warn("Department not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Lỗi logic khác của quản lý nhân sự (HR Management)
    @ExceptionHandler(HRManagementException.class)
    public ResponseEntity<Map<String, Object>> handleHRManagementException(HRManagementException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "HR Management Error");
        response.put("message", ex.getMessage());

        log.error("HR Management error: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(response);
    }

    // ========== ATTENDANCE EXCEPTIONS ==========

    // Lỗi không tìm thấy attendance
    @ExceptionHandler(AttendanceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAttendanceNotFoundException(AttendanceNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Attendance Not Found");
        response.put("message", ex.getMessage());

        log.warn("Attendance not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // Lỗi validation attendance (face, WiFi, ...)
    @ExceptionHandler(AttendanceValidationException.class)
    public ResponseEntity<Map<String, Object>> handleAttendanceValidationException(AttendanceValidationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Attendance Validation Failed");
        response.put("message", ex.getMessage());

        log.warn("Attendance validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    // Lỗi đã check-in rồi
    @ExceptionHandler(AlreadyCheckedInException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyCheckedInException(AlreadyCheckedInException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Already Checked In");
        response.put("message", ex.getMessage());

        log.warn("Already checked in: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Lỗi face verification failed
    @ExceptionHandler(FaceVerificationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleFaceVerificationFailedException(FaceVerificationFailedException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", "Face Verification Failed");
        response.put("message", ex.getMessage());

        log.warn("Face verification failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // Lỗi WiFi validation failed
    @ExceptionHandler(WiFiValidationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleWiFiValidationFailedException(WiFiValidationFailedException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", "WiFi Validation Failed");
        response.put("message", ex.getMessage());

        log.warn("WiFi validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // Lỗi logic khác của Attendance
    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<Map<String, Object>> handleAttendanceException(AttendanceException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Attendance Error");
        response.put("message", ex.getMessage());

        log.error("Attendance error: {}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(response);
    }

    /*
     Xử lý tất cả exceptions khác
     Tác dụng: Catch-all để đảm bảo không có exception nào bị leak
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Internal Server Error");
        response.put("message", "An unexpected error occurred");

        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    //Handle xử lí lỗi ném ra từ SQL:
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleUniqueConstraint(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        Map<String, String> errors = new HashMap<>();

        // Check constraint trong thông báo SQL để xác định field trùng
        if (message != null) {
            String msgLower = message.toLowerCase();

            // Check trùng phone trong DB
            if (msgLower.contains("uq_users_phone") || msgLower.contains("uq__users__phone")) {
                errors.put("phone", "Phone number is already registered");
            } else {
                errors.put("general", "Duplicate data in unique field");
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", "Duplicate field value");
        body.put("errors", errors);

        log.warn("Unique constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Helper nội bộ trả response 409 conflict
    private ResponseEntity<Map<String, Object>> buildConflictResponse(String field, String message) {
        Map<String, String> errors = new HashMap<>();
        errors.put(field, message);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", message);
        body.put("errors", errors);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Xử lí validation trùng email
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(DuplicateEmailException ex) {
        return buildConflictResponse("email", ex.getMessage());
    }

    // Xử lí validate trùng username
    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUsername(DuplicateUsernameException ex) {
        return buildConflictResponse("username", ex.getMessage());
    }

    //Handle xử lí lỗi ném ra từ SQL:
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleUniqueConstraint(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        Map<String, String> errors = new HashMap<>();

        // Check constraint trong thông báo SQL để xác định field trùng
        if (message != null) {
            String msgLower = message.toLowerCase();

            // Check trùng phone trong DB
            if (msgLower.contains("uq_users_phone") || msgLower.contains("uq__users__phone")) {
                errors.put("phone", "Phone number is already registered");
            } else {
                errors.put("general", "Duplicate data in unique field");
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", "Duplicate field value");
        body.put("errors", errors);

        log.warn("Unique constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Helper nội bộ trả response 409 conflict
    private ResponseEntity<Map<String, Object>> buildConflictResponse(String field, String message) {
        Map<String, String> errors = new HashMap<>();
        errors.put(field, message);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("message", message);
        body.put("errors", errors);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // Xử lí validation BadCredentialsException cho phương thức login bên ServiceImp
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", ex.getMessage()); // "Invalid email or password" | "Account is disabled"

        Map<String, String> errors = new HashMap<>();
        String m = ex.getMessage() != null ? ex.getMessage() : "Invalid email or password";
        errors.put("email", m);
        errors.put("password", m);
        body.put("errors", errors);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    // Xử lí validation trùng email
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(DuplicateEmailException ex) {
        return buildConflictResponse("email", ex.getMessage());
    }

    // Xử lí validate trùng username
    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUsername(DuplicateUsernameException ex) {
        return buildConflictResponse("username", ex.getMessage());
    }
}
