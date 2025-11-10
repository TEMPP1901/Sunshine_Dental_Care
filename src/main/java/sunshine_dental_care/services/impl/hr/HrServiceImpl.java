package sunshine_dental_care.services.impl.hr;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.hrDTO.CreateWeeklyScheduleRequest;
import sunshine_dental_care.dto.hrDTO.DoctorScheduleDto;
import sunshine_dental_care.dto.hrDTO.ValidationResultDto;
import sunshine_dental_care.entities.Chair;
import sunshine_dental_care.entities.Clinic;
import sunshine_dental_care.entities.DoctorSchedule;
import sunshine_dental_care.entities.Room;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.exceptions.hr.ScheduleValidationException;
import sunshine_dental_care.repositories.hr.DoctorScheduleRepo;
import sunshine_dental_care.services.interfaces.hr.HrService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrServiceImpl implements HrService {
    
    private final DoctorScheduleRepo doctorScheduleRepo;
    private final EntityManager entityManager;
    
    @Override
    @Transactional
    public List<DoctorScheduleDto> createWeeklySchedule(CreateWeeklyScheduleRequest request) {
        log.info("Creating weekly schedule for week starting: {}", request.getWeekStart());
        
        // Xác thực yêu cầu trước
        ValidationResultDto validation = validateSchedule(request);
        if (!validation.isValid()) {
            log.warn("Schedule validation failed: {}", validation.getErrors());
            throw new ScheduleValidationException(String.join(", ", validation.getErrors()));
        }
        
        List<DoctorSchedule> schedules = new ArrayList<>();
        
        // Tạo lịch cho từng ngày (T2-T7)
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        
        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex);
            
            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = 
                request.getDailyAssignments().get(dayName);
            
            if (dayAssignments != null) {
                for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                    DoctorSchedule schedule = new DoctorSchedule();
                    
                    // Đặt thông tin cơ bản - tạo entity đơn giản để tránh serialization issues
                    User doctor = new User();
                    doctor.setId(assignment.getDoctorId());
                    schedule.setDoctor(doctor);
                    
                    Clinic clinic = new Clinic();
                    clinic.setId(assignment.getClinicId());
                    schedule.setClinic(clinic);
                    
                    Room room = new Room();
                    room.setId(assignment.getRoomId());
                    schedule.setRoom(room);
                    
                    Chair chair = new Chair();
                    chair.setId(assignment.getChairId());
                    schedule.setChair(chair);
                    
                    // Đặt thông tin ngày và giờ
                    schedule.setWorkDate(workDate);
                    schedule.setStartTime(assignment.getStartTime());
                    schedule.setEndTime(assignment.getEndTime());
                    
                    // Đặt trạng thái và ghi chú
                    schedule.setStatus("ACTIVE");
                    schedule.setNote(assignment.getNote());
                    
                    // Đặt timestamp
                    schedule.setCreatedAt(java.time.Instant.now());
                    schedule.setUpdatedAt(java.time.Instant.now());
                    
                    schedules.add(schedule);
                }
            }
        }
        
        // Lưu tất cả các lịch trình
        List<DoctorSchedule> savedSchedules = doctorScheduleRepo.saveAll(schedules);
        
        // Chuyển đổi sang DTO
        return savedSchedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getCurrentWeekSchedule() {
        LocalDate today = LocalDate.now();
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWorkDate(today);
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getScheduleByWeek(LocalDate weekStart) {
        // Lấy lịch cho ngày đầu tuần (thứ 2)
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWorkDate(weekStart);
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleDto> getNextWeekSchedule() {
        LocalDate nextWeekStart = LocalDate.now().plusWeeks(1);
        List<DoctorSchedule> schedules = doctorScheduleRepo.findByWorkDate(nextWeekStart);
        return schedules.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public ValidationResultDto validateSchedule(CreateWeeklyScheduleRequest request) {
        ValidationResultDto result = new ValidationResultDto();
        
        // Xác thực ngày trong tuần
        LocalDate weekEnd = request.getWeekStart().plusDays(6); // Thứ 2 + 6 ngày = Thứ 7 (Saturaday)
        // Không cần kiểm tra logic sai này vì weekStart + 6 luôn > weekStart
        
        // 1. Kiểm tra có phân công không
        if (request.getDailyAssignments() == null || request.getDailyAssignments().isEmpty()) {
            result.addError("No daily assignments provided for the week");
        }
        
        // 2. Xác thực từng ngày trong tuần (T2-T7)
        String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        
        for (int dayIndex = 0; dayIndex < 6; dayIndex++) {
            String dayName = days[dayIndex];
            LocalDate workDate = request.getWeekStart().plusDays(dayIndex); // T2+0=T2, T2+1=T3, ...
            
            List<CreateWeeklyScheduleRequest.DoctorAssignmentRequest> dayAssignments = 
                request.getDailyAssignments().get(dayName);
            
            // 2a. Kiểm tra ngày có phân công không
            if (dayAssignments == null || dayAssignments.isEmpty()) {
                result.addError("No assignments for " + dayName);
                continue;
            }
            
            // 2b. Kiểm tra: MỖI NGÀY PHẢI CÓ ĐÚNG 3 BÁC SĨ
            if (dayAssignments.size() != 3) {
                result.addError("Must have exactly 3 doctors for " + dayName + ", found " + dayAssignments.size());
            }
            
            // 2c. Kiểm tra: TẤT CẢ BÁC SĨ CÙNG PHÒNG KHÁM
            Integer clinicId = dayAssignments.get(0).getClinicId();
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                if (!assignment.getClinicId().equals(clinicId)) {
                    result.addError("All doctors must be in same clinic for " + dayName);
                    break;
                }
            }
            
            // 2d. Kiểm tra từng bác sĩ trong ngày
            Set<Integer> doctorIds = new HashSet<>();
            
            for (CreateWeeklyScheduleRequest.DoctorAssignmentRequest assignment : dayAssignments) {
                // 2d-1. Kiểm tra BÁC SĨ TRÙNG LẶP trong cùng ngày
                if (!doctorIds.add(assignment.getDoctorId())) {
                    result.addError("Doctor ID " + assignment.getDoctorId() + " is assigned multiple times on " + dayName);
                }
                
                // 2d-2. Kiểm tra BÁC SĨ ĐÃ ĐƯỢC PHÂN CÔNG (trong database)
                if (doctorScheduleRepo.existsByDoctorAndDate(assignment.getDoctorId(), workDate)) {
                    String errorMsg = "Doctor ID " + assignment.getDoctorId() + " is already assigned on " + workDate;
                    result.addError(errorMsg);
                    log.warn("Doctor conflict detected: {}", errorMsg);
                }
                
                // 2d-3. Kiểm tra THỜI GIAN hợp lệ (startTime < endTime)
                if (assignment.getStartTime().isAfter(assignment.getEndTime())) {
                    result.addError("Start time must be before end time for doctor ID " + assignment.getDoctorId() + " on " + dayName);
                }
            }
        }
        
        return result;
    }
    
    // Các phương thức hỗ trợ
    private DoctorScheduleDto convertToDto(DoctorSchedule schedule) {
        DoctorScheduleDto dto = new DoctorScheduleDto();
        dto.setId(schedule.getId());
        
        // Chuyển đổi User sang Object
        if (schedule.getDoctor() != null) {
            dto.setDoctor(schedule.getDoctor());
        }
        
        // Chuyển đổi Clinic sang Object
        if (schedule.getClinic() != null) {
            dto.setClinic(schedule.getClinic());
        }
        
        // Chuyển đổi Room sang Object
        if (schedule.getRoom() != null) {
            dto.setRoom(schedule.getRoom());
        }
        
        // Chuyển đổi Chair sang Object
        if (schedule.getChair() != null) {
            dto.setChair(schedule.getChair());
        }
        
        // Đặt các trường khác
        dto.setWorkDate(schedule.getWorkDate());
        dto.setStartTime(schedule.getStartTime());
        dto.setEndTime(schedule.getEndTime());
        dto.setStatus(schedule.getStatus());
        dto.setNote(schedule.getNote());
        
        // Chuyển đổi dấu thời gian
        if (schedule.getCreatedAt() != null) {
            dto.setCreatedAt(schedule.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
        }
        if (schedule.getUpdatedAt() != null) {
            dto.setUpdatedAt(schedule.getUpdatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
        }
        
        return dto;
    }
}
