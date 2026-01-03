package sunshine_dental_care.exceptions.auth;

import lombok.Getter;

@Getter
public class DuplicateEmailException extends AuthException {
    private final String email;

    public DuplicateEmailException(String email) {
        super("Email is already registered");
        this.email = email;
    }

}
