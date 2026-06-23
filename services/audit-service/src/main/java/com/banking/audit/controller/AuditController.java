package com.banking.audit.controller;

import com.banking.audit.entity.AuditLog;
import com.banking.audit.repository.AuditLogRepository;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Immutable audit trail — ADMIN/AUDITOR only")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit logs for a user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getUserAuditLogs(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                auditLogRepository.findRecentByUserId(userId)));
    }

    @GetMapping("/user/{userId}/range")
    @Operation(summary = "Get audit logs in a time range")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AUDITOR')")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogsInRange(
            @PathVariable String userId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(ApiResponse.success(
                auditLogRepository.findByUserIdAndTimeRange(userId, from, to)));
    }
}
