package sunshine_dental_care.repositories.doctor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.MedicalRecord;
import sunshine_dental_care.entities.MedicalRecordImage;
import sunshine_dental_care.entities.Patient;

@Repository
@Transactional(readOnly = true)
public interface PatientInsightRepository
        extends JpaRepository<Appointment, Integer> {

    /* =====================================================
     * APPOINTMENTS – LỊCH HẸN
     * ===================================================== */

    @Query("""
        SELECT a
        FROM Appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
        ORDER BY a.startDateTime DESC
    """)
    List<Appointment> completedAppointmentsByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT a
        FROM Appointment a
        WHERE a.patient.id = :patientId
          AND a.startDateTime > CURRENT_TIMESTAMP
          AND a.status IN ('BOOKED','CONFIRMED')
        ORDER BY a.startDateTime ASC
    """)
    List<Appointment> upcomingAppointmentsByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT a
        FROM Appointment a
        WHERE a.patient.id = :patientId
          AND a.startDateTime < CURRENT_TIMESTAMP
          AND a.status NOT IN ('COMPLETED','CANCELLED')
        ORDER BY a.startDateTime DESC
    """)
    List<Appointment> overdueAppointmentsByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT MAX(a.startDateTime)
        FROM Appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
    """)
    Instant lastVisitByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT COUNT(a)
        FROM Appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
    """)
    long totalVisitsByPatientId(
            @Param("patientId") Integer patientId
    );

    /* =====================================================
     * APPOINTMENT SERVICES – DỊCH VỤ
     * ===================================================== */

    @Query("""
        SELECT s
        FROM AppointmentService s
        JOIN s.appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
    """)
    List<AppointmentService> usedServicesByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT s
        FROM AppointmentService s
        JOIN FETCH s.service svc
        LEFT JOIN FETCH s.serviceVariant var
        WHERE s.appointment.id = :appointmentId
        ORDER BY s.id ASC
    """)
    List<AppointmentService> appointmentServicesByAppointmentId(
            @Param("appointmentId") Integer appointmentId
    );

    // Tìm tất cả dịch vụ của một lịch hẹn (chỉ cần appointmentId)
    @Query("""
        SELECT s
        FROM AppointmentService s
        WHERE s.appointment.id = :appointmentId
        ORDER BY s.id ASC
    """)
    List<AppointmentService> findAllServicesByAppointmentId(
            @Param("appointmentId") Integer appointmentId
    );

    // Tìm dịch vụ theo serviceId
    @Query("""
        SELECT s
        FROM AppointmentService s
        JOIN FETCH s.appointment a
        JOIN FETCH s.service svc
        LEFT JOIN FETCH s.serviceVariant var
        WHERE svc.id = :serviceId
        ORDER BY a.startDateTime DESC, s.id ASC
    """)
    List<AppointmentService> appointmentServicesByServiceId(
            @Param("serviceId") Integer serviceId
    );

    // Tìm dịch vụ theo variantId
    @Query("""
        SELECT s
        FROM AppointmentService s
        JOIN FETCH s.appointment a
        JOIN FETCH s.service svc
        JOIN FETCH s.serviceVariant var
        WHERE var.id = :variantId
        ORDER BY a.startDateTime DESC, s.id ASC
    """)
    List<AppointmentService> appointmentServicesByVariantId(
            @Param("variantId") Integer variantId
    );

    @Query("""
        SELECT s
        FROM AppointmentService s
        JOIN s.appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
        ORDER BY a.startDateTime DESC
    """)
    List<AppointmentService> latestUsedServicesByPatientId(
            @Param("patientId") Integer patientId
    );

    // Tìm dịch vụ với điều kiện chi tiết (quantity, unitPrice, discountPct, note)
    @Query("""
        SELECT s
        FROM AppointmentService s
        JOIN FETCH s.appointment a
        JOIN FETCH s.service svc
        LEFT JOIN FETCH s.serviceVariant var
        WHERE (:quantity IS NULL OR s.quantity = :quantity)
          AND (:minPrice IS NULL OR s.unitPrice >= :minPrice)
          AND (:maxPrice IS NULL OR s.unitPrice <= :maxPrice)
          AND (:discountPct IS NULL OR s.discountPct = :discountPct)
          AND (:noteKeyword IS NULL OR LOWER(s.note) LIKE LOWER(CONCAT('%', :noteKeyword, '%')))
        ORDER BY a.startDateTime DESC, s.id ASC
    """)
    List<AppointmentService> findServicesWithDetails(
            @Param("quantity") Integer quantity,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("discountPct") BigDecimal discountPct,
            @Param("noteKeyword") String noteKeyword
    );

    @Query("""
        SELECT SUM(
            s.quantity * s.unitPrice *
            (1 - COALESCE(s.discountPct, 0) / 100)
        )
        FROM AppointmentService s
        WHERE s.appointment.id = :appointmentId
    """)
    BigDecimal appointmentTotalCost(
            @Param("appointmentId") Integer appointmentId
    );

    /* =====================================================
     * MEDICAL RECORDS – BỆNH ÁN
     * ===================================================== */

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.patient.id = :patientId
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalHistoryByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.appointment.id = :appointmentId
    """)
    List<MedicalRecord> medicalRecordByAppointmentId(
            @Param("appointmentId") Integer appointmentId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.id = :recordId
    """)
    Optional<MedicalRecord> medicalRecordById(
            @Param("recordId") Integer recordId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.patient.id = :patientId
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.doctor.id = :doctorId
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByDoctorId(
            @Param("doctorId") Integer doctorId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.service.id = :serviceId
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByServiceId(
            @Param("serviceId") Integer serviceId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.serviceVariant.id = :variantId
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByVariantId(
            @Param("variantId") Integer variantId
    );

    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.appointmentService.id = :appointmentServiceId
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByAppointmentServiceId(
            @Param("appointmentServiceId") Integer appointmentServiceId
    );

    // Tìm hồ sơ theo ngày (recordDate)
    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.recordDate = :recordDate
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByDate(
            @Param("recordDate") LocalDate recordDate
    );

    // Tìm hồ sơ trong khoảng thời gian
    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE m.recordDate BETWEEN :startDate AND :endDate
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> medicalRecordsByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Tìm hồ sơ với tìm kiếm theo nội dung chi tiết
    @Query("""
SELECT m
FROM MedicalRecord m
WHERE LOWER(m.diagnosis) LIKE LOWER(CONCAT('%', :keyword, '%'))
   OR LOWER(m.treatmentPlan) LIKE LOWER(CONCAT('%', :keyword, '%'))
   OR LOWER(m.prescriptionNote) LIKE LOWER(CONCAT('%', :keyword, '%'))
""")
    List<MedicalRecord> medicalRecordsByKeyword(@Param("keyword") String keyword);




    // Tìm hồ sơ với điều kiện cụ thể
    @Query("""
        SELECT m
        FROM MedicalRecord m
        WHERE (:patientId IS NULL OR m.patient.id = :patientId)
          AND (:doctorId IS NULL OR m.doctor.id = :doctorId)
          AND (:appointmentId IS NULL OR m.appointment.id = :appointmentId)
          AND (:serviceId IS NULL OR m.service.id = :serviceId)
          AND (:variantId IS NULL OR m.serviceVariant.id = :variantId)
          AND (:appointmentServiceId IS NULL OR m.appointmentService.id = :appointmentServiceId)
          AND (:diagnosisKeyword IS NULL OR LOWER(m.diagnosis) LIKE LOWER(CONCAT('%', :diagnosisKeyword, '%')))
          AND (:treatmentKeyword IS NULL OR LOWER(m.treatmentPlan) LIKE LOWER(CONCAT('%', :treatmentKeyword, '%')))
          AND (:prescriptionKeyword IS NULL OR LOWER(m.prescriptionNote) LIKE LOWER(CONCAT('%', :prescriptionKeyword, '%')))
          AND (:noteKeyword IS NULL OR LOWER(m.note) LIKE LOWER(CONCAT('%', :noteKeyword, '%')))
        ORDER BY m.recordDate DESC
    """)
    List<MedicalRecord> searchMedicalRecords(
            @Param("patientId") Integer patientId,
            @Param("doctorId") Integer doctorId,
            @Param("appointmentId") Integer appointmentId,
            @Param("serviceId") Integer serviceId,
            @Param("variantId") Integer variantId,
            @Param("appointmentServiceId") Integer appointmentServiceId,
            @Param("diagnosisKeyword") String diagnosisKeyword,
            @Param("treatmentKeyword") String treatmentKeyword,
            @Param("prescriptionKeyword") String prescriptionKeyword,
            @Param("noteKeyword") String noteKeyword
    );

    /* =====================================================
     * MEDICAL IMAGES – HÌNH ẢNH
     * ===================================================== */

    @Query("""
        SELECT i
        FROM MedicalRecordImage i
        WHERE i.medicalRecord.id = :recordId
        ORDER BY i.createdAt DESC
    """)
    List<MedicalRecordImage> medicalImagesByRecordId(
            @Param("recordId") Integer recordId
    );

    @Query("""
        SELECT i
        FROM MedicalRecordImage i
        WHERE i.medicalRecord.patient.id = :patientId
        ORDER BY i.createdAt DESC
    """)
    List<MedicalRecordImage> medicalImagesByPatientId(
            @Param("patientId") Integer patientId
    );

    /* =====================================================
     * PATIENTS – BỆNH NHÂN
     * ===================================================== */

    // Tìm bệnh nhân theo ID
    @Query("""
        SELECT p
        FROM Patient p
        WHERE p.id = :patientId
    """)
    Optional<Patient> findPatientById(
            @Param("patientId") Integer patientId
    );

    // Tìm bệnh nhân theo tên (tìm kiếm gần đúng)
    @Query("""
        SELECT p
        FROM Patient p
        WHERE LOWER(p.fullName) LIKE LOWER(CONCAT('%', :nameKeyword, '%'))
        ORDER BY p.fullName ASC
    """)
    List<Patient> findPatientsByName(
            @Param("nameKeyword") String nameKeyword
    );

    // Tìm bệnh nhân theo giới tính
    @Query("""
        SELECT p
        FROM Patient p
        WHERE p.gender = :gender
        ORDER BY p.fullName ASC
    """)
    List<Patient> findPatientsByGender(
            @Param("gender") String gender
    );

    // Tìm bệnh nhân theo ngày sinh
    @Query("""
        SELECT p
        FROM Patient p
        WHERE p.dateOfBirth = :dateOfBirth
        ORDER BY p.fullName ASC
    """)
    List<Patient> findPatientsByDateOfBirth(
            @Param("dateOfBirth") LocalDate dateOfBirth
    );

    // Tìm bệnh nhân theo số điện thoại
    @Query("""
        SELECT p
        FROM Patient p
        WHERE p.phone LIKE CONCAT('%', :phone, '%')
        ORDER BY p.fullName ASC
    """)
    List<Patient> findPatientsByPhone(
            @Param("phone") String phone
    );

    // Tìm bệnh nhân theo email
    @Query("""
        SELECT p
        FROM Patient p
        WHERE LOWER(p.email) LIKE LOWER(CONCAT('%', :email, '%'))
        ORDER BY p.fullName ASC
    """)
    List<Patient> findPatientsByEmail(
            @Param("email") String email
    );

    // Tìm bệnh nhân với nhiều điều kiện
    @Query("""
        SELECT p
        FROM Patient p
        WHERE (:nameKeyword IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :nameKeyword, '%')))
          AND (:gender IS NULL OR p.gender = :gender)
          AND (:phoneKeyword IS NULL OR p.phone LIKE CONCAT('%', :phoneKeyword, '%'))
          AND (:emailKeyword IS NULL OR LOWER(p.email) LIKE LOWER(CONCAT('%', :emailKeyword, '%')))
        ORDER BY p.fullName ASC
    """)
    List<Patient> searchPatients(
            @Param("nameKeyword") String nameKeyword,
            @Param("gender") String gender,
            @Param("phoneKeyword") String phoneKeyword,
            @Param("emailKeyword") String emailKeyword
    );

    /* =====================================================
     * ANALYTICS – THỐNG KÊ
     * ===================================================== */

    @Query("""
        SELECT a.doctor.id, COUNT(a)
        FROM Appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
        GROUP BY a.doctor.id
        ORDER BY COUNT(a) DESC
    """)
    List<Object[]> mostVisitedDoctorByPatientId(
            @Param("patientId") Integer patientId
    );

    @Query("""
        SELECT s.service.id, COUNT(s)
        FROM AppointmentService s
        JOIN s.appointment a
        WHERE a.patient.id = :patientId
          AND a.status = 'COMPLETED'
        GROUP BY s.service.id
        ORDER BY COUNT(s) DESC
    """)
    List<Object[]> mostUsedServiceByPatientId(
            @Param("patientId") Integer patientId
    );

    // Thống kê dịch vụ theo appointmentId
    @Query("""
        SELECT s.service.serviceName, COUNT(s), SUM(s.quantity), 
               SUM(s.quantity * s.unitPrice * (1 - COALESCE(s.discountPct, 0) / 100))
        FROM AppointmentService s
        WHERE s.appointment.id = :appointmentId
        GROUP BY s.service.serviceName
    """)
    List<Object[]> serviceStatsByAppointmentId(
            @Param("appointmentId") Integer appointmentId
    );
}