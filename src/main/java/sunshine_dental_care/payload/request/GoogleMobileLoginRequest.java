package sunshine_dental_care.payload.request;

import lombok.Data;

@Data
public class GoogleMobileLoginRequest {
    private String idToken;
    private String email;
    private String fullName;
    private String avatarUrl;
}