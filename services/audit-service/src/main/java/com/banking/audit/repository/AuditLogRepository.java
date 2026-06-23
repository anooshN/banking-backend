package com.banking.audit.repository;

import com.banking.audit.entity.AuditLog;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends CassandraRepository<AuditLog, UUID> {

    @Query("SELECT * FROM audit_logs WHERE user_id = ?0 AND event_time >= ?1 AND event_time <= ?2 ORDER BY event_time DESC LIMIT 100")
    List<AuditLog> findByUserIdAndTimeRange(String userId, Instant from, Instant to);

    @Query("SELECT * FROM audit_logs WHERE user_id = ?0 ORDER BY event_time DESC LIMIT 50")
    List<AuditLog> findRecentByUserId(String userId);
}
