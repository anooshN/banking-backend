package com.banking.common.constants;

public final class BankingConstants {

    private BankingConstants() {}

    // API versioning
    public static final String API_V1 = "/api/v1";
    public static final String API_V2 = "/api/v2";

    // Security
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    // Roles
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
    public static final String ROLE_TELLER = "ROLE_TELLER";
    public static final String ROLE_AUDITOR = "ROLE_AUDITOR";

    // Kafka Topics
    public static final String TOPIC_ACCOUNT_EVENTS = "banking.account.events";
    public static final String TOPIC_TRANSACTION_EVENTS = "banking.transaction.events";
    public static final String TOPIC_PAYMENT_EVENTS = "banking.payment.events";
    public static final String TOPIC_FRAUD_EVENTS = "banking.fraud.events";
    public static final String TOPIC_NOTIFICATION_EVENTS = "banking.notification.events";
    public static final String TOPIC_AUDIT_EVENTS = "banking.audit.events";
    public static final String TOPIC_USER_EVENTS = "banking.user.events";

    // DLQ Topics
    public static final String TOPIC_DLQ_SUFFIX = ".dlq";

    // Cache Keys
    public static final String CACHE_ACCOUNT = "account";
    public static final String CACHE_USER = "user";
    public static final String CACHE_TRANSACTION = "transaction";

    // Transaction types
    public static final String TXN_DEBIT = "DEBIT";
    public static final String TXN_CREDIT = "CREDIT";
    public static final String TXN_TRANSFER = "TRANSFER";

    // Payment rails
    public static final String PAYMENT_SWIFT = "SWIFT";
    public static final String PAYMENT_FED = "FEDWIRE";
    public static final String PAYMENT_CHIPS = "CHIPS";
    public static final String PAYMENT_INTERNAL = "INTERNAL";

    // Account types
    public static final String ACCOUNT_CHECKING = "CHECKING";
    public static final String ACCOUNT_SAVINGS = "SAVINGS";
    public static final String ACCOUNT_INVESTMENT = "INVESTMENT";

    // Pagination defaults
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}
