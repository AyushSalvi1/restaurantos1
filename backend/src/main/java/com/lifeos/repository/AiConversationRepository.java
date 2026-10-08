package com.lifeos.repository;

import com.lifeos.entity.AiConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {

    Optional<AiConversation> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    Page<AiConversation> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String userId, Pageable pageable);
}