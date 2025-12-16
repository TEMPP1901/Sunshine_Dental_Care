package sunshine_dental_care.services.interfaces.reception;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.AppointmentUpdateRequest;
import sunshine_dental_care.dto.receptionDTO.BillInvoiceDTO;
import sunshine_dental_care.dto.receptionDTO.PatientHistoryDTO;
import sunshine_dental_care.dto.receptionDTO.PatientRequest;
import sunshine_dental_care.dto.receptionDTO.PatientResponse;
import sunshine_dental_care.dto.receptionDTO.RescheduleRequest;
import sunshine_dental_care.security.CurrentUser;

public interface ReceptionService {
    List<DoctorScheduleDto> getDoctorSchedulesForView
            (CurrentUser currentUser, LocalDate date, Integer requestClinicId);

    AppointmentResponse createNewAppointment(
            CurrentUser currentUser,
            AppointmentRequest request
    );

    List<AppointmentResponse> getAppointmentsForDashboard(
            CurrentUser currentUser,
            LocalDate date,
            Integer requestedClinicId
    );

    AppointmentResponse rescheduleAppointment(
            CurrentUser currentUser,
            Integer appointmentId,
            RescheduleRequest request
    );

    Page<PatientResponse> getPatients(String keyword, int page, int size);

    PatientResponse createPatient(PatientRequest request);

    AppointmentResponse updateAppointment(Integer appointmentId, AppointmentUpdateRequest request);

    AppointmentResponse assignRoomToAppointment(Integer appointmentId, Integer roomId);

    // 1. Lấy chi tiết hóa đơn (để in hoặc xem trước)
    BillInvoiceDTO getBillDetails(Integer appointmentId);

    // 2. Xác nhận đã thanh toán hoàn tất (Lễ tân bấm nút "Đã thu tiền")
    void confirmPayment(CurrentUser currentUser, Integer appointmentId);

    // hàm search danh sách các bảng lịch hẹn
    Page<AppointmentResponse> getAppointmentList(
            CurrentUser currentUser,
            String keyword,
            String paymentStatus,
            String status,
            LocalDate date,
            int page,
            int size
    );

    // --- MỚI: QUẢN LÝ BỆNH NHÂN ---
    PatientResponse getPatientDetail(Integer id);

    PatientResponse updatePatient(Integer id, PatientResponse request);

    List<PatientHistoryDTO> getPatientHistory(Integer patientId);
}
