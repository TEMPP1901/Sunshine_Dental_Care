package sunshine_dental_care.services.interfaces.system;

import org.springframework.data.domain.Page;

import sunshine_dental_care.entities.Log;
import sunshine_dental_care.entities.User;

public interface AuditLogService {
    // Ghi log chi tiết hành động cho các thao tác quan trọng (ghi cả dữ liệu cũ/mới)
    void logAction(User user, String action, String tableName, Integer recordId, String oldData, String newData);

    // Ghi log cơ bản cho hành động (không ghi dữ liệu cũ/mới)
    void logAction(User user, String action, String tableName, Integer recordId);

    // Lấy danh sách log kèm bộ lọc
    Page<Log> getAuditLogs(Integer userId, String action, String tableName, java.time.LocalDate fromDate,
            java.time.LocalDate toDate, int page, int size);
}
