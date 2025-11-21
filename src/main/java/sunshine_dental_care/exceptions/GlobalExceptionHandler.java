package sunshine_dental_care.exceptions;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import sunshine_dental_care.exceptions.hr.DoctorNotAvailableException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeNotFoundException;
import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeValidationException;
import sunshine_dental_care.exceptions.hr.ScheduleException;
import sunshine_dental_care.exceptions.hr.ScheduleValidationException;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /*
      Xử lý validation errors từ @Valid
    Tác dụng: Trả về chi tiết lỗi validation cho frontend
     */
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
        return ResponseEntity.badRequest().body(response);
    }

    /*
      Xử lý Schedule Validation Exception
     Tác dụng: Trả về lỗi validation schedule với chi tiết
     */
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

    /*
     Xử lý Doctor Not Available Exception
     Tác dụng: Trả về lỗi conflict bác sĩ với thông tin chi tiết
     */
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

    /*
     Xử lý Schedule Exception chung
      Tác dụng: Xử lý các lỗi schedule khác
     */
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

    /*
     Xử lý Employee Validation Exception
     Tác dụng: Trả về lỗi validation nhân viên
     */
    @ExceptionHandler(EmployeeValidationException.class)
    public ResponseEntity<Map<String, Object>> handleEmployeeValidationException(EmployeeValidationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Employee Validation Failed");
        response.put("message", ex.getMessage());
        
        log.warn("Employee validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    /*
     Xử lý Employee Exception chung
     Tác dụng: Xử lý các lỗi nhân viên khác
     */
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

    /*
     Xử lý BadCredentialsException (Spring Security)
     Tác dụng: Xử lý lỗi authentication
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", "Authentication Failed");
        response.put("message", ex.getMessage());
        
        log.warn("Bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
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
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
