package sunshine_dental_care.services.doctor.impl;

public class AiResponseInvalidException extends RuntimeException {
    public AiResponseInvalidException(String message) {
        super(message);
    }
    public AiResponseInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
