package sunshine_dental_care.dto.receptionDTO.bookingDto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicDoctorDTO {
    private Integer id;
    private String fullName;
    private String avatarUrl;
    private String specialty;
}
