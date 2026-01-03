package sunshine_dental_care.dto.doctorDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorAppointmentDTO {
    private Integer appointmentId;

    private ClinicDTO clinic;
    private PatientDTO patient;
    private DoctorDTO doctor;
    private RoomDTO room;

    private Instant startDateTime;
    private Instant endDateTime;

    private String status;
    private String channel;
    private String note;

    private Integer createdById;
    private String createdByName;

    private Instant createdAt;
    private Instant updatedAt;

    private ServiceDTO service;
    private List<String> serviceDetails; // list để hứng nhiều dịch vụ
    private ServiceVariantDTO serviceVariant; // Variant từ AppointmentService

    private String appointmentType; // "VIP" hoặc "STANDARD"
    private BigDecimal bookingFee; // Phí đặt lịch hẹn
}
