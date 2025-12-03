package sunshine_dental_care.dto.adminDTO;

import jakarta.validation.constraints.NotBlank;

public class AttendanceStatusUpdateRequest {

    @NotBlank(message = "newStatus is required")
    private String newStatus;

    private String adminNote;

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote;
    }
}

