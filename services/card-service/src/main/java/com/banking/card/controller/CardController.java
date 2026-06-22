package com.banking.card.controller;

import com.banking.card.entity.Card;
import com.banking.card.service.CardService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;

    @GetMapping
    @Operation(summary = "Get all cards for current user")
    public ResponseEntity<ApiResponse<List<Card>>> getMyCards(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(cardService.getUserCards(UUID.fromString(userId))));
    }

    @PostMapping("/issue")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TELLER')")
    @Operation(summary = "Issue a new card")
    public ResponseEntity<ApiResponse<Card>> issueCard(
            @RequestParam UUID accountId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam Card.CardType type,
            @RequestParam String cardholderName) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.issueCard(accountId, UUID.fromString(userId), type, cardholderName),
                "Card issued successfully"));
    }

    @PatchMapping("/{cardId}/block")
    @Operation(summary = "Block a card")
    public ResponseEntity<ApiResponse<Card>> blockCard(
            @PathVariable UUID cardId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.blockCard(cardId, UUID.fromString(userId)),
                "Card blocked successfully"));
    }

    @PatchMapping("/{cardId}/limits")
    @Operation(summary = "Update card limits")
    public ResponseEntity<ApiResponse<Card>> updateLimits(
            @PathVariable UUID cardId,
            @RequestParam BigDecimal dailyLimit,
            @RequestParam BigDecimal monthlyLimit) {
        return ResponseEntity.ok(ApiResponse.success(
                cardService.updateLimits(cardId, dailyLimit, monthlyLimit)));
    }
}
