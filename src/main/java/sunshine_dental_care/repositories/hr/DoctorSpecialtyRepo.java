package sunshine_dental_care.repositories.hr;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import sunshine_dental_care.entities.DoctorSpecialty;

@Repository
public interface DoctorSpecialtyRepo extends JpaRepository<DoctorSpecialty, Integer> {
    
    // Lấy tất cả chuyên khoa của một bác sĩ
    @Query("SELECT ds FROM DoctorSpecialty ds WHERE ds.doctor.id = :doctorId AND ds.isActive = true ORDER BY ds.specialtyName")
    List<DoctorSpecialty> findByDoctorIdAndIsActiveTrue(@Param("doctorId") Integer doctorId);
    
    // Lấy tất cả chuyên khoa của một bác sĩ (kể cả inactive)
    @Query("SELECT ds FROM DoctorSpecialty ds WHERE ds.doctor.id = :doctorId ORDER BY ds.specialtyName")
    List<DoctorSpecialty> findByDoctorId(@Param("doctorId") Integer doctorId);
    
    // Kiểm tra bác sĩ có chuyên khoa cụ thể không
    @Query("SELECT COUNT(ds) > 0 FROM DoctorSpecialty ds WHERE ds.doctor.id = :doctorId AND LOWER(TRIM(ds.specialtyName)) = LOWER(TRIM(:specialtyName)) AND ds.isActive = true")
    boolean existsByDoctorIdAndSpecialtyName(@Param("doctorId") Integer doctorId, @Param("specialtyName") String specialtyName);
    
    // Xóa tất cả chuyên khoa của một bác sĩ
    void deleteByDoctorId(Integer doctorId);
    
    // Lấy tất cả bác sĩ có chuyên khoa cụ thể
    @Query("SELECT DISTINCT ds.doctor.id FROM DoctorSpecialty ds WHERE LOWER(TRIM(ds.specialtyName)) = LOWER(TRIM(:specialtyName)) AND ds.isActive = true")
    List<Integer> findDoctorIdsBySpecialtyName(@Param("specialtyName") String specialtyName);
    
    // Lấy tất cả chuyên khoa của nhiều bác sĩ cùng lúc (tối ưu cho batch loading)
    @Query("SELECT ds FROM DoctorSpecialty ds WHERE ds.doctor.id IN :doctorIds AND ds.isActive = true ORDER BY ds.doctor.id, ds.specialtyName")
    List<DoctorSpecialty> findByDoctorIdInAndIsActiveTrue(@Param("doctorIds") List<Integer> doctorIds);
}

