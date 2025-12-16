package sunshine_dental_care.services.doctor;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lowagie.text.DocumentException;

import lombok.RequiredArgsConstructor;
import sunshine_dental_care.dto.doctorDTO.ClinicDTO;
import sunshine_dental_care.dto.doctorDTO.DoctorDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordImageDTO;
import sunshine_dental_care.dto.doctorDTO.MedicalRecordRequest;
import sunshine_dental_care.dto.doctorDTO.PatientDTO;
import sunshine_dental_care.dto.doctorDTO.ServiceDTO;
import sunshine_dental_care.dto.doctorDTO.ServiceVariantDTO;
import sunshine_dental_care.entities.*;
import sunshine_dental_care.repositories.auth.ClinicRepo;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.doctor.AppointmentRepository;
import sunshine_dental_care.repositories.doctor.DentalServiceRepository;
import sunshine_dental_care.repositories.doctor.DoctorRepo;
import sunshine_dental_care.repositories.doctor.MedicalRecordRepository;
import sunshine_dental_care.services.upload_file.ImageStorageService;

@Service
@RequiredArgsConstructor
public class PatientMedicalRecordServiceImpl implements PatientMedicalRecordService {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/jpg",
            "image/webp"
    );

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepo patientRepo;
    private final ClinicRepo clinicRepo;
    private final DoctorRepo doctorRepo;
    private final AppointmentRepository appointmentRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final  ImageStorageService imageStorageService;

    // Lấy danh sách hồ sơ bệnh án của một bệnh nhân
    @Override
    public List<MedicalRecordDTO> getRecords(Integer patientId) {
        // Lấy thông tin bệnh nhân, nếu không có thì báo lỗi
        Patient patient = getPatient(patientId);
        // Lấy danh sách hồ sơ bệnh án theo bệnh nhân đã sắp xếp giảm dần theo ngày tạo, sau đó chuyển sang DTO
        return medicalRecordRepository.findByPatientIdOrderByRecordDateDesc(patient.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // Build a simple list of service detail strings for the medical record
    private List<String> toServiceDetails(MedicalRecord record) {
        List<String> details = new ArrayList<>();

        // If medical record is linked to an appointment, prefer appointment services
        if (record.getAppointment() != null && record.getAppointment().getAppointmentServices() != null
                && !record.getAppointment().getAppointmentServices().isEmpty()) {
            for (AppointmentService apptService : record.getAppointment().getAppointmentServices()) {
                StringBuilder sb = new StringBuilder();
                if (apptService.getService() != null) {
                    sb.append(apptService.getService().getServiceName());
                } else {
                    sb.append("Unknown Service");
                }
                if (apptService.getServiceVariant() != null) {
                    sb.append(" (").append(apptService.getServiceVariant().getVariantName()).append(")");
                }
                sb.append(" x").append(apptService.getQuantity());
                if (apptService.getUnitPrice() != null) {
                    sb.append(" - ").append(apptService.getUnitPrice().toPlainString());
                }
                if (apptService.getNote() != null && !apptService.getNote().isBlank()) {
                    sb.append(" (Note: ").append(apptService.getNote()).append(")");
                }
                details.add(sb.toString());
            }
            return details;
        }

        // Fallback: if MedicalRecord has a direct service reference
        if (record.getService() != null) {
            details.add(record.getService().getServiceName());
            return details;
        }

        return List.of();
    }

    // Tạo mới một hồ sơ bệnh án cho bệnh nhân
    @Override
    @Transactional
    public MedicalRecordDTO createRecord(Integer patientId, MedicalRecordRequest request) {
        // Lấy các thông tin liên quan từ id (bệnh nhân, phòng khám, bác sĩ, lịch hẹn, dịch vụ)
        Patient patient = getPatient(patientId);
        Clinic clinic = getClinic(request.clinicId());
        User doctor = getDoctor(request.doctorId());
        Appointment appointment = getAppointment(request.appointmentId());
        sunshine_dental_care.entities.Service service = getService(request.serviceId());

        // Tạo mới entity MedicalRecord và set giá trị
        MedicalRecord record = new MedicalRecord();
        record.setPatient(patient);
        record.setClinic(clinic);
        record.setDoctor(doctor);
        record.setAppointment(appointment);
        record.setService(service);
        applyRequest(record, request); // set các trường thông tin khác

        // Thiết lập thời gian tạo và cập nhật
        Instant now = Instant.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        // Lưu hồ sơ vào database và trả kết quả dạng DTO
        MedicalRecord saved = medicalRecordRepository.save(record);
        return toDTO(saved);
    }

    // Cập nhật hồ sơ bệnh án cho bệnh nhân
    @Override
    @Transactional
    public MedicalRecordDTO updateRecord(Integer patientId, Integer recordId, MedicalRecordRequest request) {
        // Lấy hồ sơ cần cập nhật, nếu không có thì báo lỗi
        MedicalRecord record = medicalRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        // Cập nhật phòng khám và bác sĩ nếu có gửi lên
        if (request.clinicId() != null) {
            record.setClinic(getClinic(request.clinicId()));
        }
        if (request.doctorId() != null) {
            record.setDoctor(getDoctor(request.doctorId()));
        }

        // Cập nhật lịch hẹn, dịch vụ nếu có
        record.setAppointment(getAppointment(request.appointmentId()));
        record.setService(getService(request.serviceId()));

        // Gán thông tin từ request và cập nhật thời gian sửa đổi
        applyRequest(record, request);
        record.setUpdatedAt(Instant.now());

        // Lưu và trả về DTO
        MedicalRecord saved = medicalRecordRepository.save(record);
        return toDTO(saved);
    }

    // Gán dữ liệu từ request vào entity MedicalRecord
    private void applyRequest(MedicalRecord record, MedicalRecordRequest request) {
        record.setDiagnosis(request.diagnosis());
        record.setTreatmentPlan(request.treatmentPlan());
        record.setPrescriptionNote(request.prescriptionNote());
        record.setNote(request.note());

        // Nếu request không có ngày, thì lấy ngày hiện tại
        LocalDate recordDate = request.recordDate() != null ? request.recordDate() : LocalDate.now();
        record.setRecordDate(recordDate);
    }

    // Upload ảnh cho hồ sơ bệnh án
    @Override
    @Transactional
    public MedicalRecordImageDTO uploadImage(Integer patientId, Integer recordId, MultipartFile file, String description, String aiTag) {
        validateImage(file);

        // Lấy hồ sơ cần đính kèm ảnh, nếu không có thì báo lỗi
        MedicalRecord record = getRecord(patientId, recordId);

        // Thực hiện upload ảnh lưu trữ lên hệ thống lưu trữ (cloud,...)
        ImageStorageService.ImageUploadResult uploadResult;
        try {
            uploadResult = imageStorageService.upload(file, "medical-records/" + recordId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload medical record image", e);
        }

        // Tạo mới entity ảnh gắn vào hồ sơ
        MedicalRecordImage image = new MedicalRecordImage();
        image.setMedicalRecord(record);
        image.setImageUrl(uploadResult.getUrl());
        image.setImagePublicId(uploadResult.getPublicId());
        image.setDescription(description);
        image.setAiTag(aiTag);
        image.setCreatedAt(Instant.now());

        // Nếu hồ sơ chưa có danh sách ảnh -> khởi tạo mới
        if (record.getMedicalRecordImages() == null) {
            record.setMedicalRecordImages(new LinkedHashSet<>());
        }
        // Thêm ảnh vào danh sách ảnh của hồ sơ
        record.getMedicalRecordImages().add(image);
        record.setUpdatedAt(Instant.now());

        // Lưu và lấy ra ảnh vừa lưu (dựa vào publicId)
        MedicalRecord saved = medicalRecordRepository.save(record);
        MedicalRecordImage persisted = saved.getMedicalRecordImages()
                .stream()
                .filter(img -> uploadResult.getPublicId().equals(img.getImagePublicId()))
                .findFirst()
                .orElse(image);

        // Đổi sang DTO để trả ra ngoài
        return toImageDTO(persisted);
    }

    // Xóa ảnh khỏi hồ sơ bệnh án
    @Override
    @Transactional
    public void deleteImage(Integer patientId, Integer recordId, Integer imageId) {
        // Lấy hồ sơ, tìm ảnh muốn xóa
        MedicalRecord record = getRecord(patientId, recordId);
        MedicalRecordImage target = record.getMedicalRecordImages()
                .stream()
                .filter(img -> imageId.equals(img.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Image not found"));

        // Nếu xóa thành công khỏi list => thực hiện xóa vật lý khỏi lưu trữ
        if (record.getMedicalRecordImages().remove(target)) {
            try {
                imageStorageService.delete(target.getImagePublicId());
            } catch (Exception e) {
                throw new RuntimeException("Failed to delete medical record image", e);
            }
            record.setUpdatedAt(Instant.now());
            medicalRecordRepository.save(record);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image size must be <= 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported image type. Allowed: jpeg, jpg, png, webp");
        }
    }

    // --- Các hàm lấy entity theo id, trả về lỗi nếu không tồn tại ---

    // Lấy bệnh nhân theo id
    private Patient getPatient(Integer patientId) {
        return patientRepo.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
    }

    // Lấy phòng khám theo id (nếu null cũng báo lỗi)
    private Clinic getClinic(Integer clinicId) {
        return Optional.ofNullable(clinicId)
                .flatMap(clinicRepo::findById)
                .orElseThrow(() -> new IllegalArgumentException("Clinic not found"));
    }

    // Lấy bác sĩ theo id (nếu null cũng báo lỗi)
    private User getDoctor(Integer doctorId) {
        return Optional.ofNullable(doctorId)
                .flatMap(doctorRepo::findById)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
    }

    // Lấy lịch hẹn theo id (có thể null)
    private Appointment getAppointment(Integer appointmentId) {
        if (appointmentId == null) {
            return null;
        }
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
    }

    // Lấy dịch vụ theo id (có thể null)
    private sunshine_dental_care.entities.Service getService(Integer serviceId) {
        if (serviceId == null) {
            return null;
        }
        return dentalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));
    }

    // --- Các hàm chuyển đổi entity sang DTO để trả dữ liệu ra ngoài ---

    // Chuyển entity MedicalRecord sang MedicalRecordDTO
    private MedicalRecordDTO toDTO(MedicalRecord record) {
        // Nếu có appointmentService, ưu tiên lấy service và variant từ đó
        sunshine_dental_care.entities.Service service = record.getService();
        ServiceVariant serviceVariant = record.getServiceVariant();

        if (record.getAppointmentService() != null) {
            service = record.getAppointmentService().getService();
            serviceVariant = record.getAppointmentService().getServiceVariant();
        }

        return MedicalRecordDTO.builder()
                .recordId(record.getId())
                .clinic(toClinicDTO(record.getClinic()))
                .patient(toPatientDTO(record.getPatient()))
                .doctor(toDoctorDTO(record.getDoctor()))
                .appointmentId(record.getAppointment() != null ? record.getAppointment().getId() : null)
                .service(toServiceDTO(service)) // Map service thành ServiceDTO object
                .serviceVariant(toServiceVariantDTO(serviceVariant)) // Map variant thành ServiceVariantDTO
                .diagnosis(record.getDiagnosis())
                .treatmentPlan(record.getTreatmentPlan())
                .prescriptionNote(record.getPrescriptionNote())
                .note(record.getNote())
                .recordDate(record.getRecordDate())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .images(toImageDTOs(record))
                .build();
    }

    // Danh sách ảnh của một hồ sơ
    private List<MedicalRecordImageDTO> toImageDTOs(MedicalRecord record) {
        if (record.getMedicalRecordImages() == null || record.getMedicalRecordImages().isEmpty()) {
            return List.of();
        }

        List<MedicalRecordImageDTO> images = new ArrayList<>();
        for (MedicalRecordImage image : record.getMedicalRecordImages()) {
            images.add(toImageDTO(image));
        }
        return images;
    }

    // Chuyển entity MedicalRecordImage sang DTO
    private MedicalRecordImageDTO toImageDTO(MedicalRecordImage image) {
        return MedicalRecordImageDTO.builder()
                .imageId(image.getId())
                .imageUrl(image.getImageUrl())
                .description(image.getDescription())
                .aiTag(image.getAiTag())
                .imagePublicId(image.getImagePublicId())
                .createdAt(image.getCreatedAt())
                .build();
    }

    // Chuyển entity Clinic sang ClinicDTO
    private ClinicDTO toClinicDTO(Clinic clinic) {
        if (clinic == null) {
            return null;
        }
        return ClinicDTO.builder()
                .id(clinic.getId())
                .clinicCode(clinic.getClinicCode())
                .clinicName(clinic.getClinicName())
                .phone(clinic.getPhone())
                .address(clinic.getAddress())
                .build();
    }

    // Chuyển entity Patient sang PatientDTO
    private PatientDTO toPatientDTO(Patient patient) {
        if (patient == null) {
            return null;
        }
        return PatientDTO.builder()
                .id(patient.getId())
                .patientCode(patient.getPatientCode())
                .fullName(patient.getFullName())
                .phone(patient.getPhone())
                .email(patient.getEmail())
                .build();
    }

    // Chuyển entity User (bác sĩ) sang DoctorDTO
    private DoctorDTO toDoctorDTO(User doctor) {
        if (doctor == null) {
            return null;
        }
        return DoctorDTO.builder()
                .id(doctor.getId())
                .code(doctor.getCode())
                .fullName(doctor.getFullName())
                .email(doctor.getEmail())
                .phone(doctor.getPhone())
                .build();
    }

    // Chuyển entity Service sang ServiceDTO
    private ServiceDTO toServiceDTO(sunshine_dental_care.entities.Service service) {
        if (service == null) {
            return null;
        }
        
        // Map variants nếu có
        List<ServiceVariantDTO> variantDTOs = null;
        if (service.getVariants() != null && !service.getVariants().isEmpty()) {
            variantDTOs = service.getVariants().stream()
                    .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                    .map(v -> ServiceVariantDTO.builder()
                            .variantId(v.getId())
                            .variantName(v.getVariantName())
                            .duration(v.getDuration())
                            .price(v.getPrice())
                            .description(v.getDescription())
                            .currency(v.getCurrency())
                            .isActive(v.getIsActive())
                            .build())
                    .collect(Collectors.toList());
        }
        
        return ServiceDTO.builder()
                .id(service.getId())
                .serviceName(service.getServiceName())
                .category(service.getCategory())
                .description(service.getDescription())
                .defaultDuration(service.getDefaultDuration())
                .isActive(service.getIsActive())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .variants(variantDTOs)
                .build();
    }

    // Export hồ sơ bệnh án ra file PDF
    @Override
    @Transactional
    public byte[] exportRecordToPDF(Integer patientId, Integer recordId) {
        // Lấy hồ sơ bệnh án từ database
        MedicalRecord record = getRecord(patientId, recordId);
        
        // Đảm bảo các lazy-loaded relationships được fetch
        // Fetch clinic
        if (record.getClinic() != null) {
            record.getClinic().getClinicName();
        }
        
        // Fetch patient
        if (record.getPatient() != null) {
            record.getPatient().getFullName();
            record.getPatient().getPatientCode();
        }
        
        // Fetch doctor
        if (record.getDoctor() != null) {
            record.getDoctor().getFullName();
        }
        
        // Fetch appointment và appointment services nếu có
        if (record.getAppointment() != null) {
            Appointment appointment = record.getAppointment();
            if (appointment.getAppointmentServices() != null) {
                for (AppointmentService apptService : appointment.getAppointmentServices()) {
                    if (apptService.getService() != null) {
                        apptService.getService().getServiceName();
                    }
                    if (apptService.getServiceVariant() != null) {
                        apptService.getServiceVariant().getVariantName();
                    }
                }
            }
        }
        
        // Fetch service nếu có
        if (record.getService() != null) {
            record.getService().getServiceName();
        }
        
        // Fetch images nếu có
        if (record.getMedicalRecordImages() != null) {
            record.getMedicalRecordImages().size();
        }

        // Tạo PDF exporter và export
        try {
            MedicalRecordPdfService medicalRecordPdfService = new MedicalRecordPdfService();
            return medicalRecordPdfService.generatePdf(record);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export medical record to PDF", e);
        }

    }

    private ServiceVariantDTO toServiceVariantDTO(ServiceVariant serviceVariant) {
        if (serviceVariant == null) {
            return null;
        }

        return ServiceVariantDTO.builder()
                .variantId(serviceVariant.getId())
                .variantName(serviceVariant.getVariantName())
                .duration(serviceVariant.getDuration())
                .price(serviceVariant.getPrice())
                .description(serviceVariant.getDescription())
                .currency(serviceVariant.getCurrency())
                .isActive(serviceVariant.getIsActive())
                .build();
    }

    // Lấy hồ sơ bệnh án theo id bệnh nhân và id hồ sơ
    private MedicalRecord getRecord(Integer patientId, Integer recordId) {
        return medicalRecordRepository.findByIdAndPatientId(recordId, patientId)
                .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));
    }

}

