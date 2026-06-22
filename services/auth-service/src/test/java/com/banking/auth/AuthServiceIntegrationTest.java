package com.banking.auth;

import com.banking.auth.dto.LoginRequest;
import com.banking.auth.dto.RegisterRequest;
import com.banking.auth.entity.User;
import com.banking.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("banking_auth_test")
            .withUsername("banking")
            .withPassword("banking-secret");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should register a new user successfully")
    void register_shouldReturnTokens() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@banking.com");
        request.setPassword("SecurePass@123");
        request.setFirstName("John");
        request.setLastName("Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("test@banking.com"));

        assertThat(userRepository.findByEmail("test@banking.com")).isPresent();
    }

    @Test
    @DisplayName("Should reject duplicate email registration")
    void register_duplicateEmail_shouldReturn409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@banking.com");
        request.setPassword("SecurePass@123");
        request.setFirstName("John");
        request.setLastName("Doe");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // Second registration with same email
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_EXISTS"));
    }

    @Test
    @DisplayName("Should login successfully")
    void login_validCredentials_shouldReturnTokens() throws Exception {
        // Register first
        RegisterRequest register = new RegisterRequest();
        register.setEmail("login@banking.com");
        register.setPassword("SecurePass@123");
        register.setFirstName("Jane");
        register.setLastName("Smith");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)));

        // Login
        LoginRequest login = new LoginRequest();
        login.setEmail("login@banking.com");
        login.setPassword("SecurePass@123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.mfaRequired").value(false));
    }

    @Test
    @DisplayName("Should lock account after 5 failed attempts")
    void login_tooManyFailedAttempts_shouldLockAccount() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setEmail("lockme@banking.com");
        register.setPassword("SecurePass@123");
        register.setFirstName("Lock");
        register.setLastName("Me");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)));

        LoginRequest badLogin = new LoginRequest();
        badLogin.setEmail("lockme@banking.com");
        badLogin.setPassword("WrongPassword!");

        // 5 failed attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should get locked
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));

        User user = userRepository.findByEmail("lockme@banking.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(User.UserStatus.LOCKED);
    }
}
