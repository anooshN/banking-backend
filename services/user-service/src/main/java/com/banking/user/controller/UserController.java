package com.banking.user.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.user.dto.UpdateProfileRequest;
import com.banking.user.entity.UserProfile;
import com.banking.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserProfile>> getMyProfile(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.getProfile(UUID.fromString(userId))));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserProfile>> updateMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.updateProfile(UUID.fromString(userId), request),
                "Profile updated successfully"));
    }

    @PostMapping("/me/kyc")
    @Operation(summary = "Submit KYC documents")
    public ResponseEntity<ApiResponse<UserProfile>> submitKyc(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String documentType,
            @RequestParam String documentNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.submitKyc(UUID.fromString(userId), documentType, documentNumber),
                "KYC submitted successfully"));
    }
}
