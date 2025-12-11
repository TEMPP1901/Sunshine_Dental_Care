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
