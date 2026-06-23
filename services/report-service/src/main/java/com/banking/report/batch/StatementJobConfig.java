package com.banking.report.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StatementJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job statementJob(Step fetchTransactionsStep, Step generateFileStep, Step uploadToS3Step) {
        return new JobBuilder("statementJob", jobRepository)
                .start(fetchTransactionsStep)
                .next(generateFileStep)
                .next(uploadToS3Step)
                .build();
    }

    @Bean
    public Step fetchTransactionsStep() {
        return new StepBuilder("fetchTransactions", jobRepository)
                .tasklet(fetchTransactionsTasklet(null, null, null, null), transactionManager)
                .build();
    }

    @Bean
    public Step generateFileStep() {
        return new StepBuilder("generateFile", jobRepository)
                .tasklet(generateFileTasklet(null), transactionManager)
                .build();
    }

    @Bean
    public Step uploadToS3Step() {
        return new StepBuilder("uploadToS3", jobRepository)
                .tasklet(uploadToS3Tasklet(null), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet fetchTransactionsTasklet(
            @Value("#{jobParameters['accountId']}") String accountId,
            @Value("#{jobParameters['fromDate']}") String fromDate,
            @Value("#{jobParameters['toDate']}") String toDate,
            @Value("#{jobParameters['format']}") String format) {
        return (contribution, chunkContext) -> {
            log.info("Fetching transactions for account: {} from: {} to: {}", accountId, fromDate, toDate);
            // In prod: call transaction-service via Feign, store in job execution context
            chunkContext.getStepContext().getStepExecution().getJobExecution()
                    .getExecutionContext().put("transactionCount", 42);
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    @StepScope
    public Tasklet generateFileTasklet(@Value("#{jobParameters['format']}") String format) {
        return (contribution, chunkContext) -> {
            log.info("Generating {} statement file", format);
            // In prod: use Apache POI (Excel), iText (PDF), or CSV writer
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    @StepScope
    public Tasklet uploadToS3Tasklet(@Value("#{jobParameters['accountId']}") String accountId) {
        return (contribution, chunkContext) -> {
            log.info("Uploading statement to S3 for account: {}", accountId);
            // In prod: upload generated file to S3
            return RepeatStatus.FINISHED;
        };
    }
}
