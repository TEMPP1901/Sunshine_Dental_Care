package sunshine_dental_care.repositories.hr;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.DoctorSchedule;

@Repository
public interface DoctorScheduleRepo extends JpaRepository<DoctorSchedule, Integer> {
    
    // Lấy lịch theo ngày cụ thể
    @Query("SELECT d FROM DoctorSchedule d WHERE d.workDate = :workDate ORDER BY d.doctor.id")
    List<DoctorSchedule> findByWorkDate(@Param("workDate") LocalDate workDate);
    
    // Lấy lịch của cơ sở theo ngày
    @Query("SELECT d FROM DoctorSchedule d WHERE d.clinic.id = :clinicId AND d.workDate = :workDate ORDER BY d.doctor.id")
    List<DoctorSchedule> findByClinicAndDate(@Param("clinicId") Integer clinicId, @Param("workDate") LocalDate workDate);
    
    // Kiểm tra bác sĩ đã được phân công chưa trong ngày
    @Query("SELECT COUNT(d) > 0 FROM DoctorSchedule d WHERE d.doctor.id = :doctorId AND d.workDate = :workDate")
    boolean existsByDoctorAndDate(@Param("doctorId") Integer doctorId, @Param("workDate") LocalDate workDate);
}