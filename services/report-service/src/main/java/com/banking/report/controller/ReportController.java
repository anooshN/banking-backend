package com.banking.report.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.report.model.StatementRequest;
import com.banking.report.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final StatementService statementService;

    @PostMapping("/statement")
    @Operation(summary = "Generate account statement")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN') or hasRole('TELLER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateStatement(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String accountId,
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam(defaultValue = "PDF") StatementRequest.StatementFormat format) throws Exception {

        StatementRequest request = new StatementRequest();
        request.setAccountId(accountId);
        request.setUserId(userId);
        request.setFromDate(LocalDate.parse(fromDate));
        request.setToDate(LocalDate.parse(toDate));
        request.setFormat(format);

        String s3Key = statementService.generateStatement(request);
        String downloadUrl = statementService.getPresignedDownloadUrl(s3Key);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("downloadUrl", downloadUrl, "s3Key", s3Key),
                "Statement generation initiated"));
    }
}
