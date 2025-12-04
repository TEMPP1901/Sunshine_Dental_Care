package sunshine_dental_care.dto.receptionDTO;
import lombok.Data;

@Data
public class AppointmentUpdateRequest {
    private String status; // CONFIRMED, COMPLETED, CANCELLED...
    private String note;
}
