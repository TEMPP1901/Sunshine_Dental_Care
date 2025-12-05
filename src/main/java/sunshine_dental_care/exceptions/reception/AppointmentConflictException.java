package sunshine_dental_care.exceptions.reception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// 409 CONFLICT - Khi lịch hẹn bị trùng với lịch hẹn/lịch làm việc khác
@ResponseStatus(HttpStatus.CONFLICT)
public class AppointmentConflictException extends RuntimeException {
    public AppointmentConflictException(String message) {
        super(message);
    }
}
