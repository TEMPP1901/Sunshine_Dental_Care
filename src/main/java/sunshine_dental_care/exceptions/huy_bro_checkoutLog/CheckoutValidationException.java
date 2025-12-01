package sunshine_dental_care.exceptions.huy_bro_checkoutLog;

import lombok.Getter;
import java.util.Map;

@Getter
public class CheckoutValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;

    public CheckoutValidationException(Map<String, String> fieldErrors) {
        super("Validation failed");
        this.fieldErrors = fieldErrors;
    }
}
