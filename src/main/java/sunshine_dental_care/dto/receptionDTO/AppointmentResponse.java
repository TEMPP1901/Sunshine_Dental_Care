package sunshine_dental_care.dto.receptionDTO;

import java.time.Instant;
import java.util.List;

import lombok.Data;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.RoomResponse;
import sunshine_dental_care.dto.hrDTO.HrDocDto;

@Data
public class AppointmentResponse {
    private Integer id;
    private ClinicResponse clinic;
    private PatientResponse patient;
    private HrDocDto doctor;
    private RoomResponse room;
    private Instant startDateTime;
    private Instant endDateTime;
    private String status;
    private String channel;
    private String note;
    private String createdByUserName;
    private Instant createdAt;
    private Instant updatedAt;

    private List<ServiceItemResponse> services;
}
