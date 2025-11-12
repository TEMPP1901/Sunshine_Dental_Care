package sunshine_dental_care.exceptions.auth;

import lombok.Getter;

@Getter
public class DuplicateUsernameException extends AuthException {
    private final String username;

    public DuplicateUsernameException(String username) {
        super("Username is already taken");
        this.username = username;
    }

}
