package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomDTO {
    private Integer id;
    private String roomName;
    private Boolean isPrivate;
    private Boolean isActive;
}

