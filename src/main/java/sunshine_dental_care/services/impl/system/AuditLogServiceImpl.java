package sunshine_dental_care.services.impl.system;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.entities.Log;
import sunshine_dental_care.entities.User;
import sunshine_dental_care.repositories.system.LogRepo;
import sunshine_dental_care.services.interfaces.system.AuditLogService;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl implements AuditLogService {

    private final LogRepo logRepo;

    // Ghi lại các tác vụ quan trọng vào bảng log (AUDIT)
    @Override
    @Transactional
    public void logAction(User user, String action, String tableName, Integer recordId, String oldData,
            String newData) {
        try {
            String ipAddr = getClientIp();
            String userAgent = getUserAgent();

            Log auditLog = Log.builder()
                    .user(user)
                    .type("AUDIT")
                    .priority("INFO")
                    .title("Audit Log: " + action)
                    .message(String.format("User %s performed %s on %s (ID: %s)",
                            user.getUsername(), action, tableName, recordId))
                    .action(action)
                    .tableName(tableName)
                    .recordId(recordId)
                    .afterData(newData)
                    .ipAddr(ipAddr)
                    .userAgent(userAgent)
                    .createdAt(Instant.now())
                    .actionTime(Instant.now())
                    .isRead(true)
                    .build();

            logRepo.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log", e);
            // Không ném exception để tránh ảnh hưởng flow nghiệp vụ chính
        }
    }

    // Log tác vụ cơ bản (không cần oldData/newData)
    @Override
    public void logAction(User user, String action, String tableName, Integer recordId) {
        logAction(user, action, tableName, recordId, null, null);
    }

    // Tìm kiếm audit log với filter động
    @Override
    public Page<Log> getAuditLogs(Integer userId, String action, String tableName, LocalDate fromDate, LocalDate toDate,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<Log> spec = (root, query, cb) -> cb.equal(root.get("type"), "AUDIT");

        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("user").get("id"), userId));
        }
        if (action != null && !action.isEmpty()) {
            spec = spec
                    .and((root, query, cb) -> cb.like(cb.lower(root.get("action")), "%" + action.toLowerCase() + "%"));
        }
        if (tableName != null && !tableName.isEmpty()) {
            spec = spec.and(
                    (root, query, cb) -> cb.like(cb.lower(root.get("tableName")), "%" + tableName.toLowerCase() + "%"));
        }
        if (fromDate != null) {
            Instant fromInstant = fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
        }
        if (toDate != null) {
            Instant toInstant = toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), toInstant));
        }

        return logRepo.findAll(spec, pageable);
    }

    // Lấy IP client hiện tại (nếu lỗi sẽ trả "UNKNOWN")
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String remoteAddr = request.getHeader("X-FORWARDED-FOR");
                if (remoteAddr == null || "".equals(remoteAddr)) {
                    remoteAddr = request.getRemoteAddr();
                }
                return remoteAddr;
            }
        } catch (Exception e) {
            // ignore
        }
        return "UNKNOWN";
    }

    // Lấy user-agent từ request hiện tại (nếu lỗi sẽ trả "UNKNOWN")
    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            // ignore
        }
        return "UNKNOWN";
    }
}
