package com.banking.report.model;

import lombok.Data;
import java.time.LocalDate;

@Data
public class StatementRequest {
    private String accountId;
    private String userId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private StatementFormat format;

    public enum StatementFormat { PDF, CSV, EXCEL }
}
