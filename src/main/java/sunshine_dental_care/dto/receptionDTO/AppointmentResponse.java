package sunshine_dental_care.dto.receptionDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Data;
import sunshine_dental_care.dto.doctorDTO.RoomDTO;
import sunshine_dental_care.dto.hrDTO.ClinicResponse;
import sunshine_dental_care.dto.hrDTO.HrDocDto;

@Data
public class AppointmentResponse {
    private Integer id;
    private ClinicResponse clinic;
    private PatientResponse patient;
    private HrDocDto doctor;
    private RoomDTO room;
    private Instant startDateTime;
    private Instant endDateTime;
    private String status;
    private String channel;
    private String note;
    private String createdByUserName;
    private Instant createdAt;
    private Instant updatedAt;
    
    private String appointmentType; // "VIP" hoặc "STANDARD"
    private BigDecimal bookingFee; // Phí đặt lịch hẹn

    private List<ServiceItemResponse> services;

    // Trường này dành riêng cho list table ở AppointmentList.tsx, phẳng dạng String để dễ filter và hiển thị
    private String patientName;
    private String patientCode;
    private String patientPhone;

    private String doctorName;
    private String clinicName;

    private String paymentStatus;

    private String invoiceCode;
    private java.math.BigDecimal totalAmount;
    private java.math.BigDecimal subTotal;
}
