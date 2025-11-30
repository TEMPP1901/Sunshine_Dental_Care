package sunshine_dental_care.services.impl.reception;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service; // Annotation của Spring
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.receptionDTO.AppointmentRequest;
import sunshine_dental_care.dto.receptionDTO.AppointmentResponse;
import sunshine_dental_care.dto.receptionDTO.RescheduleRequest;
import sunshine_dental_care.dto.receptionDTO.ServiceItemRequest;
import sunshine_dental_care.dto.receptionDTO.mapper.AppointmentMapper;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.exceptions.reception.AccessDeniedException;
import sunshine_dental_care.exceptions.reception.AppointmentConflictException;
import sunshine_dental_care.exceptions.reception.ResourceNotFoundException;

import sunshine_dental_care.dto.receptionDTO.mapper.DoctorScheduleMapper;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.auth.UserRepo;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.repositories.hr.RoomRepo;
import sunshine_dental_care.repositories.hr.UserClinicAssignmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentRepo;
import sunshine_dental_care.repositories.reception.AppointmentServiceRepo;
import sunshine_dental_care.repositories.reception.ServiceVariantRepo;
import sunshine_dental_care.repositories.system.LogRepo;
import sunshine_dental_care.security.CurrentUser;
import sunshine_dental_care.services.interfaces.reception.ReceptionService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionServiceImpl implements ReceptionService {

    private final UserClinicAssignmentRepo userClinicAssignmentRepo;
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final AppointmentRepo appointmentRepo;
    private final PatientRepo patientRepo;
    private final RoomRepo roomRepo;
    private final UserRepo userRepo;
    private final ClinicRepo clinicRepo;
    private final AppointmentServiceRepo appointmentServiceRepo;
    private final LogRepo logRepo;
    private final ServiceVariantRepo serviceVariantRepo;

    private final DoctorScheduleMapper doctorScheduleMapper;
    private final AppointmentMapper appointmentMapper;

    private Integer getReceptionistClinicId(CurrentUser currentUser) {
        if (currentUser == null) throw new AccessDeniedException("User context is missing.");
        List<UserClinicAssignment> assignments = userClinicAssignmentRepo.findByUserId(currentUser.userId());
        UserClinicAssignment primaryAssignment = assignments.stream()
                .filter(a -> a.getIsPrimary() != null && a.getIsPrimary()).findFirst().orElse(null);
        if (primaryAssignment == null && !assignments.isEmpty()) primaryAssignment = assignments.getFirst();
        if (primaryAssignment == null || primaryAssignment.getClinic() == null) throw new AccessDeniedException("No clinic assigned.");
        return primaryAssignment.getClinic().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getDoctorSchedulesForView(CurrentUser currentUser, LocalDate date, Integer requestedClinicId) {
        Integer defaultClinicId = getReceptionistClinicId(currentUser);
        Integer targetClinicId = (requestedClinicId != null && requestedClinicId > 0) ? requestedClinicId : defaultClinicId;
        return doctorScheduleRepo.findByClinicAndDate(targetClinicId, date).stream()
                .map(doctorScheduleMapper::mapToScheduleDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsForDashboard(CurrentUser currentUser, LocalDate date, Integer requestedClinicId) {
        Integer defaultClinicId = getReceptionistClinicId(currentUser);
        Integer targetClinicId = (requestedClinicId != null && requestedClinicId > 0) ? requestedClinicId : defaultClinicId;
        return appointmentRepo.findByClinicIdAndDate(targetClinicId, date).stream()
                .map(appointmentMapper::mapToAppointmentResponse).collect(Collectors.toList());
    }

    private void validateDoctorWorkingHours(Integer doctorId, Integer clinicId, Instant start, Instant end) {
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate bookingDate = start.atZone(zoneId).toLocalDate();
        java.time.LocalTime bookingStartTime = start.atZone(zoneId).toLocalTime();
        java.time.LocalTime bookingEndTime = end.atZone(zoneId).toLocalTime();

        List<DoctorSchedule> doctorSchedules = doctorScheduleRepo.findByUserIdAndClinicIdAndWorkDate(
                doctorId, clinicId, bookingDate
        );

        boolean isWithinWorkingHours = false;
        for (DoctorSchedule schedule : doctorSchedules) {
            if (!bookingStartTime.isBefore(schedule.getStartTime()) &&
                    !bookingEndTime.isAfter(schedule.getEndTime())) {
                isWithinWorkingHours = true;
                break;
            }
        }

        if (!isWithinWorkingHours) {
            throw new AppointmentConflictException("Thời gian (" + bookingStartTime + "-" + bookingEndTime + ") nằm ngoài ca làm việc của bác sĩ.");
        }
    }

    @Override
    @Transactional
    public AppointmentResponse createNewAppointment(CurrentUser currentUser, AppointmentRequest request) {
        User creator = userRepo.getReferenceById(currentUser.userId());
        Clinic clinic = clinicRepo.findById(request.getClinicId()).orElseThrow(() -> new ResourceNotFoundException("Clinic not found."));
        User doctor = userRepo.findById(request.getDoctorId()).orElseThrow(() -> new ResourceNotFoundException("Doctor not found."));
        Room room = (request.getRoomId() != null) ? roomRepo.findById(request.getRoomId()).orElseThrow(() -> new ResourceNotFoundException("Room not found.")) : null;

        Patient patient;
        if (request.getPatientId() != null && request.getPatientId() > 0) {
            patient = patientRepo.findById(request.getPatientId()).orElseThrow(() -> new ResourceNotFoundException("Patient not found"));
        } else {
            patient = patientRepo.findByUserId(currentUser.userId()).orElseThrow(() -> new ResourceNotFoundException("Patient profile not found"));
        }

        long totalDurationMinutes = request.getServices().stream()
                .mapToLong(req -> {
                    // Tìm Variant theo ID
                    sunshine_dental_care.entities.ServiceVariant v = serviceVariantRepo.findById(req.getServiceId())
                            .orElseThrow(() -> new ResourceNotFoundException("Service Variant not found: " + req.getServiceId()));
                    // Lấy duration từ Variant
                    return (long) v.getDuration() * req.getQuantity();
                })
                .sum();

        Instant start = request.getStartDateTime();
        Instant end = start.plusSeconds(TimeUnit.MINUTES.toSeconds(totalDurationMinutes));

        validateDoctorWorkingHours(request.getDoctorId(), request.getClinicId(), start, end);

        Integer roomIdToCheck = (room != null) ? room.getId() : null;
        List<Appointment> conflicts = appointmentRepo.findConflictAppointments(request.getDoctorId(), roomIdToCheck, start, end);
        if (!conflicts.isEmpty()) throw new AppointmentConflictException("Bác sĩ đã có lịch hẹn khác.");

        Appointment appointment = new Appointment();
        appointment.setClinic(clinic);
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setRoom(room);
        appointment.setStartDateTime(start);
        appointment.setEndDateTime(end);
        appointment.setStatus(request.getStatus());
        appointment.setChannel(request.getChannel() != null ? request.getChannel() : "Walk-in");
        appointment.setNote(request.getNote());
        appointment.setCreatedBy(creator);
        appointment = appointmentRepo.save(appointment);

        for (ServiceItemRequest req : request.getServices()) {
            sunshine_dental_care.entities.ServiceVariant v = serviceVariantRepo.findById(req.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service Variant not found"));

            AppointmentService as = new AppointmentService();
            as.setAppointment(appointment);
            as.setService(v.getService());
            as.setQuantity(req.getQuantity());
            as.setUnitPrice(v.getPrice()); // Lấy giá từ Variant
            as.setDiscountPct(req.getDiscountPct());
            String note = req.getNote() != null ? req.getNote() : "";
            as.setNote(note + " [" + v.getVariantName() + "]");
            appointmentServiceRepo.save(as);
        }
        return appointmentMapper.mapToAppointmentResponse(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponse rescheduleAppointment(CurrentUser currentUser, Integer appointmentId, RescheduleRequest request) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        User targetDoctor = appointment.getDoctor();
        if (request.getNewDoctorId() != null && !request.getNewDoctorId().equals(targetDoctor.getId())) {
            targetDoctor = userRepo.findById(request.getNewDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target doctor not found"));
        }

        Instant oldStart = appointment.getStartDateTime();
        Instant oldEnd = appointment.getEndDateTime();
        long durationSeconds = oldEnd.getEpochSecond() - oldStart.getEpochSecond();

        Instant newStart = request.getNewStartDateTime();
        Instant newEnd = newStart.plusSeconds(durationSeconds);

        validateDoctorWorkingHours(targetDoctor.getId(), appointment.getClinic().getId(), newStart, newEnd);

        List<Appointment> conflicts = appointmentRepo.findConflictAppointments(
                targetDoctor.getId(),
                null,
                newStart,
                newEnd
        );

        boolean hasRealConflict = conflicts.stream().anyMatch(a -> !a.getId().equals(appointmentId));

        if (hasRealConflict) {
            throw new AppointmentConflictException("Khung giờ mới đã bị trùng lịch hẹn khác.");
        }

        appointment.setDoctor(targetDoctor);
        appointment.setStartDateTime(newStart);
        appointment.setEndDateTime(newEnd);

        if (!targetDoctor.getId().equals(appointment.getDoctor().getId())) {
            appointment.setRoom(null);
        }

        if (request.getReason() != null) {
            // Tạo bản ghi Log mới
            Log actionLog = new Log();

            // Set quan hệ
            actionLog.setUser(userRepo.getReferenceById(currentUser.userId()));
            actionLog.setClinic(appointment.getClinic());

            actionLog.setTableName("Appointments");
            actionLog.setRecordId(appointment.getId());
            actionLog.setAction("RESCHEDULE");

            // Lưu lý do vào
            actionLog.setAfterData("Reschedule Reason: " + request.getReason());

            // thời gian được @PrePersist tự tạo

            logRepo.save(actionLog);
        }

        return appointmentMapper.mapToAppointmentResponse(appointmentRepo.save(appointment));
    }
}