package com.banking.audit.entity;

import lombok.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.time.Instant;
import java.util.UUID;

@Table("audit_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditLog {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "event_time", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private Instant eventTime;

    @PrimaryKeyColumn(name = "event_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    private UUID eventId;

    @Column("action")
    private String action;

    @Column("resource")
    private String resource;

    @Column("resource_id")
    private String resourceId;

    @Column("status")
    private String status;

    @Column("correlation_id")
    private String correlationId;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("duration_ms")
    private Long durationMs;

    @Column("error_message")
    private String errorMessage;

    @Column("metadata")
    private String metadata;
}
