package com.banking.report.service;

import com.banking.report.model.StatementRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final JobLauncher jobLauncher;
    private final Job statementJob;
    private final S3Client s3Client;

    private static final String BUCKET = System.getenv().getOrDefault("S3_BUCKET", "banking-statements");

    public String generateStatement(StatementRequest request) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString("accountId", request.getAccountId())
                .addString("userId", request.getUserId())
                .addString("fromDate", request.getFromDate().toString())
                .addString("toDate", request.getToDate().toString())
                .addString("format", request.getFormat().name())
                .addString("runId", UUID.randomUUID().toString())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(statementJob, params);
        log.info("Statement job {} - status: {}", execution.getId(), execution.getStatus());

        String s3Key = String.format("statements/%s/%s-%s.%s",
                request.getUserId(),
                request.getAccountId(),
                request.getToDate(),
                request.getFormat().name().toLowerCase());

        return s3Key;
    }

    public String getPresignedDownloadUrl(String s3Key) {
        // In prod: use S3Presigner
        return String.format("https://%s.s3.amazonaws.com/%s", BUCKET, s3Key);
    }

    // Run end-of-month statements for all accounts automatically
    @Scheduled(cron = "0 0 1 1 * *") // 1st of every month at 01:00
    public void generateMonthlyStatements() {
        log.info("Starting monthly statement generation batch job");
        // In prod: query all active accounts and kick off batch
    }
}
