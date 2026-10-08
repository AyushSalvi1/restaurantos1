package com.lifeos.repository;

import com.lifeos.entity.AiMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiMessageRepository extends JpaRepository<AiMessage, String> {

    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    Page<AiMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);

    void deleteByConversationId(String conversationId);
}