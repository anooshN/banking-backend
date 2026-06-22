package com.banking.card.service;

import com.banking.card.entity.Card;
import com.banking.card.repository.CardRepository;
import com.banking.common.exception.BankingException;
import com.banking.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public List<Card> getUserCards(UUID userId) {
        return cardRepository.findByUserId(userId);
    }

    @Transactional
    public Card issueCard(UUID accountId, UUID userId, Card.CardType type, String cardholderName) {
        Card card = Card.builder()
                .accountId(accountId)
                .userId(userId)
                .cardType(type)
                .cardNumberMasked("**** **** **** " + generateLast4())
                .cardholderName(cardholderName.toUpperCase())
                .expiryDate(LocalDate.now().plusYears(3))
                .status(Card.CardStatus.ACTIVE)
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("50000.00"))
                .internationalEnabled(false)
                .contactlessEnabled(true)
                .onlineEnabled(true)
                .build();
        log.info("Card issued for user: {} type: {}", userId, type);
        return cardRepository.save(card);
    }

    @Transactional
    public Card blockCard(UUID cardId, UUID userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId.toString()));
        if (!card.getUserId().equals(userId)) {
            throw new BankingException("Unauthorized card access", "UNAUTHORIZED", HttpStatus.FORBIDDEN);
        }
        card.setStatus(Card.CardStatus.BLOCKED);
        log.warn("Card blocked: {} by user: {}", cardId, userId);
        return cardRepository.save(card);
    }

    @Transactional
    public Card updateLimits(UUID cardId, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId.toString()));
        card.setDailyLimit(dailyLimit);
        card.setMonthlyLimit(monthlyLimit);
        return cardRepository.save(card);
    }

    private String generateLast4() {
        return String.format("%04d", (int)(Math.random() * 10000));
    }
}
