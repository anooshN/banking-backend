package com.banking.card.repository;

import com.banking.card.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {
    List<Card> findByUserId(UUID userId);
    List<Card> findByAccountId(UUID accountId);
    List<Card> findByUserIdAndStatus(UUID userId, Card.CardStatus status);
}
