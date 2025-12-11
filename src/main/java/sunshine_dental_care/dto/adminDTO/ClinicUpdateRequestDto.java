package sunshine_dental_care.dto.adminDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClinicUpdateRequestDto {

    @NotBlank(message = "Clinic code is required")
    @Size(max = 50, message = "Clinic code must not exceed 50 characters")
    private String clinicCode;

    @NotBlank(message = "Clinic name is required")
    @Size(max = 200, message = "Clinic name must not exceed 200 characters")
    private String clinicName;

    @Size(max = 300, message = "Address must not exceed 300 characters")
    private String address;

    @Size(max = 50, message = "Phone must not exceed 50 characters")
    private String phone;

    @Size(max = 120, message = "Email must not exceed 120 characters")
    private String email;

    @Size(max = 200, message = "Opening hours must not exceed 200 characters")
    private String openingHours;

    public String getClinicCode() {
        return clinicCode;
    }

    public void setClinicCode(String clinicCode) {
        this.clinicCode = clinicCode;
    }

    public String getClinicName() {
        return clinicName;
    }

    public void setClinicName(String clinicName) {
        this.clinicName = clinicName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }
}

