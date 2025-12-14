package sunshine_dental_care.repositories.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import sunshine_dental_care.entities.Appointment;

@org.springframework.stereotype.Repository
public interface AdminAppointmentStatsRepository extends Repository<Appointment, Integer> {

    // Đếm tổng số lịch hẹn theo trạng thái chỉ định trong một khoảng thời gian
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.startDateTime >= :start AND a.startDateTime < :end
              AND a.status IN :statuses
            """)
    long countByStartBetweenAndStatusIn(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("statuses") List<String> statuses
    );

    // Đếm số lịch hẹn bị hủy hoặc không đến trong một khoảng thời gian
    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.startDateTime >= :start AND a.startDateTime < :end
              AND a.status IN ('CANCELLED', 'NO_SHOW')
            """)
    long countCancelledBetween(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Lấy số lượng từng trạng thái lịch hẹn trong một khoảng thời gian
    @Query("""
            SELECT a.status AS status, COUNT(a) AS total
            FROM Appointment a
            WHERE a.startDateTime >= :start AND a.startDateTime < :end
            GROUP BY a.status
            """)
    List<StatusCountView> countByStatusBetween(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Lấy số lượng lịch theo kênh đặt lịch trong khoảng thời gian
    @Query("""
            SELECT a.channel AS channel, COUNT(a) AS total
            FROM Appointment a
            WHERE a.startDateTime >= :start AND a.startDateTime < :end
            GROUP BY a.channel
            """)
    List<ChannelCountView> countByChannelBetween(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Lấy danh sách id bệnh nhân duy nhất có lịch trong khoảng thời gian, không tính các lịch bị hủy, no show
    @Query("""
            SELECT DISTINCT a.patient.id
            FROM Appointment a
            WHERE a.patient.id IS NOT NULL
              AND a.startDateTime >= :start AND a.startDateTime < :end
              AND (a.status IS NULL OR a.status NOT IN ('CANCELLED', 'NO_SHOW'))
            """)
    List<Integer> findDistinctPatientIdsInRange(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Lấy danh sách id bệnh nhân duy nhất có lịch trước một thời điểm, không tính các lịch bị hủy, no show
    @Query("""
            SELECT DISTINCT a.patient.id
            FROM Appointment a
            WHERE a.patient.id IS NOT NULL
              AND a.startDateTime < :before
              AND (a.status IS NULL OR a.status NOT IN ('CANCELLED', 'NO_SHOW'))
            """)
    List<Integer> findDistinctPatientIdsBefore(
            @Param("before") Instant before
    );

    // Tính tỷ lệ quay lại khám: đếm số bệnh nhân có lịch trong khoảng thời gian và cũng có lịch trước đó
    @Query("""
            SELECT COUNT(DISTINCT a1.patient.id)
            FROM Appointment a1
            WHERE a1.patient.id IS NOT NULL
              AND a1.startDateTime >= :start AND a1.startDateTime < :end
              AND (a1.status IS NULL OR a1.status NOT IN ('CANCELLED', 'NO_SHOW'))
              AND EXISTS (
                  SELECT 1 FROM Appointment a2
                  WHERE a2.patient.id = a1.patient.id
                    AND a2.startDateTime < :start
                    AND (a2.status IS NULL OR a2.status NOT IN ('CANCELLED', 'NO_SHOW'))
              )
            """)
    long countReturningPatients(
            @Param("start") Instant start,
            @Param("end") Instant end
    );
    
    // Đếm số bệnh nhân duy nhất có lịch trong khoảng thời gian
    @Query("""
            SELECT COUNT(DISTINCT a.patient.id)
            FROM Appointment a
            WHERE a.patient.id IS NOT NULL
              AND a.startDateTime >= :start AND a.startDateTime < :end
              AND (a.status IS NULL OR a.status NOT IN ('CANCELLED', 'NO_SHOW'))
            """)
    long countDistinctPatientsInRange(
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    // Interface trả về cho truy vấn đếm theo trạng thái
    interface StatusCountView {
        String getStatus();
        Long getTotal();
    }

    // Interface trả về cho truy vấn đếm theo kênh
    interface ChannelCountView {
        String getChannel();
        Long getTotal();
    }
}
