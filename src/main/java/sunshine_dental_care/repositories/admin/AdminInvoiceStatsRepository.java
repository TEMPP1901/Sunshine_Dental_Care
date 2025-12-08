package sunshine_dental_care.repositories.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import sunshine_dental_care.entities.huybro_product_invoice.ProductInvoice;

@org.springframework.stereotype.Repository
public interface AdminInvoiceStatsRepository extends Repository<ProductInvoice, Integer> {

    // Lấy tổng doanh thu trong khoảng ngày với trạng thái thanh toán hợp lệ
    @Query("""
            SELECT COALESCE(SUM(p.totalAmount), 0) FROM ProductInvoice p
            WHERE p.invoiceDate BETWEEN :startDate AND :endDate
              AND p.paymentStatus IN ('PAID', 'COMPLETED')
            """)
    BigDecimal sumRevenueBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Lấy doanh thu từng ngày trong khoảng, phục vụ cho hiển thị biểu đồ doanh thu 7 ngày
    @Query("""
            SELECT p.invoiceDate AS invoiceDate,
                   COALESCE(SUM(p.totalAmount), 0) AS revenue,
                   COUNT(p) AS orderCount
            FROM ProductInvoice p
            WHERE p.invoiceDate BETWEEN :startDate AND :endDate
              AND p.paymentStatus IN ('PAID', 'COMPLETED')
            GROUP BY p.invoiceDate
            ORDER BY p.invoiceDate
            """)
    List<DailyRevenueView> findDailyRevenueBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // Thống kê doanh thu theo bác sĩ (dựa trên appointment liên kết) trong khoảng ngày
    @Query("""
            SELECT p.appointment.doctor.id AS doctorId,
                   p.appointment.doctor.fullName AS doctorName,
                   COALESCE(SUM(p.totalAmount), 0) AS revenue,
                   COUNT(p) AS completedAppointments
            FROM ProductInvoice p
            WHERE p.invoiceDate BETWEEN :startDate AND :endDate
              AND p.paymentStatus IN ('PAID', 'COMPLETED')
              AND p.appointment IS NOT NULL
              AND p.appointment.doctor IS NOT NULL
              AND p.appointment.doctor.id IS NOT NULL
            GROUP BY p.appointment.doctor.id, p.appointment.doctor.fullName
            ORDER BY revenue DESC
            """)
    List<DoctorRevenueView> findDoctorRevenueBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    interface DailyRevenueView {
        LocalDate getInvoiceDate();
        BigDecimal getRevenue();
        Long getOrderCount();
    }

    interface DoctorRevenueView {
        Integer getDoctorId();
        String getDoctorName();
        BigDecimal getRevenue();
        Long getCompletedAppointments();
    }
}
