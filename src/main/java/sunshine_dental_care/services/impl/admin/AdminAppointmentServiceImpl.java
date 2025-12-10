package sunshine_dental_care.services.impl.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.mapper.AppointmentMapper;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.services.interfaces.admin.AdminAppointmentService;

@Service
@RequiredArgsConstructor
public class AdminAppointmentServiceImpl implements AdminAppointmentService {

    private final AppointmentRepo appointmentRepo;
    private final AppointmentMapper appointmentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(LocalDate date, Integer clinicId) {
        // Lấy danh sách lịch hẹn cho admin theo ngày và phòng khám (nếu có)
        if (clinicId != null && clinicId > 0) {
            // Nếu có clinicId, chỉ lấy lịch hẹn của phòng khám đó
            return appointmentRepo.findByClinicIdAndDate(clinicId, date).stream()
                    .map(appointmentMapper::mapToAppointmentResponse)
                    .collect(Collectors.toList());
        } else {
            // Nếu không có clinicId, lấy tất cả lịch hẹn của tất cả phòng khám theo ngày
            return appointmentRepo.findAllByDate(date).stream()
                    .map(appointmentMapper::mapToAppointmentResponse)
                    .collect(Collectors.toList());
        }
    }
}
