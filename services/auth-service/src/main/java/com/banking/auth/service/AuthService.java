package com.banking.auth.service;

import com.banking.auth.dto.AuthResponse;
import com.banking.auth.dto.LoginRequest;
import com.banking.auth.dto.RegisterRequest;
import com.banking.auth.entity.User;
import com.banking.auth.repository.UserRepository;
import com.banking.common.constants.BankingConstants;
import com.banking.common.exception.BankingException;
import com.banking.kafka.producer.BankingEventProducer;
import com.banking.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, String> redisTemplate;
    private final BankingEventProducer eventProducer;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String REFRESH_PREFIX = "refresh:";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BankingException("Email already registered", "EMAIL_EXISTS", HttpStatus.CONFLICT);
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .roles(Set.of(BankingConstants.ROLE_CUSTOMER))
                .status(User.UserStatus.ACTIVE)
                .mfaEnabled(false)
                .failedLoginAttempts(0)
                .build();
        user = userRepository.save(user);
        eventProducer.publishEvent(BankingConstants.TOPIC_USER_EVENTS, user.getId().toString(),
                "USER_REGISTERED:" + user.getEmail());
        log.info("User registered: {}", user.getEmail());
        return generateTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BankingException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED));

        if (user.getStatus() == User.UserStatus.LOCKED) {
            if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
                throw new BankingException("Account locked. Try again later", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN);
            }
            user.setStatus(User.UserStatus.ACTIVE);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new BankingException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
        }

        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        if (user.isMfaEnabled()) {
            return AuthResponse.builder().mfaRequired(true).userId(user.getId().toString()).build();
        }

        return generateTokens(user);
    }

    public AuthResponse refreshToken(String refreshToken) {
        String storedUserId = redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
        if (storedUserId == null || !jwtTokenProvider.validateToken(refreshToken)) {
            throw new BankingException("Invalid refresh token", "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }
        redisTemplate.delete(REFRESH_PREFIX + refreshToken);
        User user = userRepository.findById(UUID.fromString(storedUserId))
                .orElseThrow(() -> new BankingException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));
        return generateTokens(user);
    }

    public void logout(String accessToken, String userId) {
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + accessToken, userId, 1, TimeUnit.HOURS);
        log.info("User logged out: {}", userId);
    }

    private AuthResponse generateTokens(User user) {
        List<String> roles = List.copyOf(user.getRoles());
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());
        redisTemplate.opsForValue().set(REFRESH_PREFIX + refreshToken,
                user.getId().toString(), 24, TimeUnit.HOURS);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .roles(roles)
                .mfaRequired(false)
                .build();
    }

    private void handleFailedLogin(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            user.setStatus(User.UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            log.warn("Account locked due to failed attempts: {}", user.getEmail());
        }
        userRepository.save(user);
    }
}
