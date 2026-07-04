package com.banking.transaction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class AccountsDataSourceConfig {

    @Value("${accounts.datasource.url:jdbc:postgresql://localhost:5433/banking_accounts}")
    private String url;

    @Value("${accounts.datasource.username:banking}")
    private String username;

    @Value("${accounts.datasource.password:banking-secret}")
    private String password;

    @Bean
    public JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return new JdbcTemplate(dataSource);
    }
}
