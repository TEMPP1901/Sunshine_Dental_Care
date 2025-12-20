package sunshine_dental_care.services.doctor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for AI-generated patient summaries.
 * Cache TTL: 12 minutes (between 10-15 min requirement)
 */
@Service
@Slf4j
public class AppointmentAISummaryCacheService {
    
    private static final long CACHE_TTL_MINUTES = 12;
    private static final long CACHE_TTL_MS = CACHE_TTL_MINUTES * 60 * 1000;
    
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    /**
     * Get cached summary for an appointment
     * @param appointmentId Appointment ID
     * @return Cached summary or null if not found/expired
     */
    public String get(Integer appointmentId) {
        String key = getCacheKey(appointmentId);
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            log.debug("Cache miss for appointmentId={}", appointmentId);
            return null;
        }
        
        if (isExpired(entry)) {
            log.debug("Cache expired for appointmentId={}, removing", appointmentId);
            cache.remove(key);
            return null;
        }
        
        log.debug("Cache hit for appointmentId={}", appointmentId);
        return entry.summary;
    }
    
    /**
     * Store summary in cache
     * @param appointmentId Appointment ID
     * @param summary AI-generated summary
     */
    public void put(Integer appointmentId, String summary) {
        String key = getCacheKey(appointmentId);
        cache.put(key, new CacheEntry(summary, Instant.now()));
        log.debug("Cached summary for appointmentId={}", appointmentId);
    }
    
    /**
     * Clear cache for a specific appointment (useful for invalidation)
     */
    public void evict(Integer appointmentId) {
        String key = getCacheKey(appointmentId);
        cache.remove(key);
        log.debug("Evicted cache for appointmentId={}", appointmentId);
    }
    
    /**
     * Clear all cache entries (useful for testing or maintenance)
     */
    public void clear() {
        cache.clear();
        log.info("Cleared all AI summary cache entries");
    }
    
    private String getCacheKey(Integer appointmentId) {
        return "ai_summary:" + appointmentId;
    }
    
    private boolean isExpired(CacheEntry entry) {
        long ageMs = Instant.now().toEpochMilli() - entry.cachedAt.toEpochMilli();
        return ageMs >= CACHE_TTL_MS;
    }
    
    private static class CacheEntry {
        final String summary;
        final Instant cachedAt;
        
        CacheEntry(String summary, Instant cachedAt) {
            this.summary = summary;
            this.cachedAt = cachedAt;
        }
    }
}
