package sunshine_dental_care.dto.adminDTO;

import jakarta.validation.constraints.NotNull;

public class ClinicActivationRequestDto {

    @NotNull(message = "active flag is required")
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

