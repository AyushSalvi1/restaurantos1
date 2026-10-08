package com.lifeos.service;

import com.lifeos.config.StorageProperties;
import com.lifeos.dto.KnowledgeDtos;
import com.lifeos.entity.KnowledgeChunk;
import com.lifeos.entity.KnowledgeDocument;
import com.lifeos.entity.SavedItem;
import com.lifeos.entity.enums.KnowledgeStatus;
import com.lifeos.entity.enums.SavedItemType;
import com.lifeos.exception.AppException;
import com.lifeos.rag.DocumentTextExtractor;
import com.lifeos.rag.EmbeddingService;
import com.lifeos.rag.RagService;
import com.lifeos.rag.TextChunker;
import com.lifeos.rag.VectorStore;
import com.lifeos.repository.KnowledgeChunkRepository;
import com.lifeos.repository.KnowledgeDocumentRepository;
import com.lifeos.repository.SavedItemRepository;
import com.lifeos.util.Csv;
import com.lifeos.util.UploadValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Document ingestion and retrieval for the personal knowledge base.
 *
 * <p>Uploads are validated by extension, size and content, stored outside the web root with
 * generated names, then chunked and embedded. Each document keeps its own status so a single bad
 * file never blocks the rest of the library.</p>
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final SavedItemRepository savedItemRepository;
    private final StorageProperties storageProperties;
    private final ApplicationEventPublisher events;
    private final DocumentTextExtractor extractor;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final RagService ragService;
    private final AiAssistantService aiAssistantService;

    public KnowledgeService(KnowledgeDocumentRepository documentRepository,
                            KnowledgeChunkRepository chunkRepository,
                            SavedItemRepository savedItemRepository,
                            StorageProperties storageProperties,
                            DocumentTextExtractor extractor,
                            TextChunker chunker,
                            EmbeddingService embeddingService,
                            VectorStore vectorStore,
                            RagService ragService,
                            AiAssistantService aiAssistantService,
                            ApplicationEventPublisher events) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.savedItemRepository = savedItemRepository;
        this.storageProperties = storageProperties;
        this.events = events;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.ragService = ragService;
        this.aiAssistantService = aiAssistantService;
    }

    // ---------------------------------------------------------------- upload

    @Transactional
    public KnowledgeDtos.DocumentResponse upload(String userId, MultipartFile file, String title) {
        Set<String> allowed = new HashSet<>(storageProperties.allowedKnowledgeExtensions());
        String extension = UploadValidation.requireExtension(file, allowed);
        UploadValidation.requireSize(file, storageProperties.maxKnowledgeFileBytes());

        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setFilename(UploadValidation.safeFilename(file.getOriginalFilename()));
        document.setExtension(extension);
        document.setTitle((title == null || title.isBlank() ? document.getFilename() : title.strip()));
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
        document.setStatus(KnowledgeStatus.PENDING);
        try {
            documentRepository.saveAndFlush(document);
        } catch (DataIntegrityViolationException ex) {
            throw AppException.conflict("A document with that name already exists");
        }

        Path target = resolveStoragePath(userId, document.getId(), document.getFilename());
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw AppException.unprocessable("The upload could not be stored");
        }

        document.setStoragePath(target.toString());
        documentRepository.save(document);
        requestIngestion(userId, document.getId());
        return toResponse(document);
    }

    /**
     * Asks for a document to be indexed once the current transaction commits, so a caller never observes
     * half-written chunks and a rollback cannot leave a document marked as being processed.
     */
    private void requestIngestion(String userId, String documentId) {
        events.publishEvent(new DocumentIngestionRequested(userId, documentId));
    }

    /** Signals that a stored document needs extraction, chunking and embedding. */
    public record DocumentIngestionRequested(String userId, String documentId) {
    }

    /** Parses, chunks and embeds an uploaded document. Runs outside the request transaction. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngestionRequested(DocumentIngestionRequested event) {
        ingest(event.userId(), event.documentId());
    }

    /**
     * Ingestion body.
     *
     * <p>This is reached only through the application-event container, never by a direct call. Calling an
     * {@code @Async} or {@code @Transactional} method from inside this class bypasses the Spring proxy, so
     * the annotations would silently do nothing and the work would run inline on the request thread.
     * Event delivery always goes through the proxy, which is what makes the annotations effective here.</p>
     */
    private void ingest(String userId, String documentId) {
        KnowledgeDocument document = documentRepository.findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElse(null);
        if (document == null) {
            return;
        }
        try {
            document.setStatus(KnowledgeStatus.PROCESSING);
            documentRepository.save(document);

            Path path = Path.of(document.getStoragePath());
            String text = extractor.extract(path, document.getExtension());
            if (text.isBlank()) {
                document.setStatus(KnowledgeStatus.FAILED);
                document.setFailureReason("No selectable text was found in this document.");
                documentRepository.save(document);
                return;
            }

            List<String> chunks = chunker.chunk(text);
            if (chunks.isEmpty()) {
                document.setStatus(KnowledgeStatus.FAILED);
                document.setFailureReason("The document did not contain enough text to index.");
                documentRepository.save(document);
                return;
            }

            chunkRepository.deleteByDocumentId(documentId);
            List<float[]> vectors = embeddingService.embed(chunks);

            List<KnowledgeChunk> entities = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocumentId(documentId);
                chunk.setUserId(userId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setTokenEstimate(chunker.estimateTokens(chunks.get(i)));
                chunk.setEmbedding(embeddingService.serialise(vectors.get(i)));
                chunk.setEmbeddingModel(embeddingService.model());
                entities.add(chunk);
            }
            List<KnowledgeChunk> saved = chunkRepository.saveAll(entities);

            vectorStore.replaceDocument(userId, documentId, saved);
            document.setChunkCount(saved.size());
            document.setWordCount(DocumentTextExtractor.wordCount(text));
            document.setStatus(KnowledgeStatus.READY);
            document.setFailureReason(null);
            documentRepository.save(document);
            log.info("Indexed document {} ({} chunks, model {})", documentId, saved.size(), embeddingService.model());
        } catch (RuntimeException ex) {
            log.warn("Ingestion failed for document {}: {}", documentId, ex.getMessage());
            documentRepository.findById(documentId).ifPresent(failed -> {
                failed.setStatus(KnowledgeStatus.FAILED);
                failed.setFailureReason(truncate(ex.getMessage()));
                documentRepository.save(failed);
            });
        }
    }

    @Transactional
    public KnowledgeDtos.DocumentResponse reprocess(String userId, String documentId) {
        KnowledgeDocument document = requireOwned(userId, documentId);
        chunkRepository.deleteByDocumentId(documentId);
        document.setStatus(KnowledgeStatus.PENDING);
        document.setFailureReason(null);
        document.setChunkCount(0);
        document.setWordCount(0);
        documentRepository.save(document);
        requestIngestion(userId, documentId);
        return toResponse(document);
    }

    @Transactional
    public void delete(String userId, String documentId) {
        KnowledgeDocument document = requireOwned(userId, documentId);
        Path path = document.getStoragePath() == null ? null : Path.of(document.getStoragePath());
        document.setDeletedAt(java.time.Instant.now());
        documentRepository.save(document);
        chunkRepository.deleteByDocumentId(documentId);
        vectorStore.removeDocument(userId, documentId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                log.warn("Could not delete stored file {}: {}", path, ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.DocumentResponse get(String userId, String documentId) {
        return toResponse(requireOwned(userId, documentId));
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.DocumentListResponse list(String userId, String query, int page, int size) {
        Page<KnowledgeDocument> result = (query == null || query.isBlank())
                ? documentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100)))
                : documentRepository.search(userId, query, PageRequest.of(Math.max(0, page),
                Math.min(Math.max(size, 1), 100)));

        long totalBytes = result.getContent().stream().mapToLong(KnowledgeDocument::getSizeBytes).sum();
        return new KnowledgeDtos.DocumentListResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                documentRepository.countByUserIdAndDeletedAtIsNull(userId),
                documentRepository.totalChunks(userId),
                totalBytes);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDtos.SearchHit> search(String userId, String query, int topK) {
        return ragService.search(userId, query, topK);
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.SearchResponse searchResponse(String userId, String query, int topK) {
        List<KnowledgeDtos.SearchHit> hits = search(userId, query, topK);
        return new KnowledgeDtos.SearchResponse(query, hits, hits.size(), !hits.isEmpty(),
                hits.isEmpty()
                        ? "No matching passages in your uploaded documents."
                        : null);
    }

    /**
     * Document-grounded question that can continue an existing conversation. See
     * {@link AiAssistantService#askGrounded} for why the conversation id is echoed back only when it
     * was actually supplied.
     */
    @Transactional
    public KnowledgeDtos.AskResponse ask(String userId, String question, String conversationId, int topK) {
        return aiAssistantService.askGrounded(userId, question, conversationId, topK);
    }

    // ------------------------------------------------------------- saved items

    @Transactional
    public KnowledgeDtos.SavedItemResponse saveItem(String userId, KnowledgeDtos.SavedItemRequest request) {
        SavedItem item = new SavedItem();
        item.setUserId(userId);
        item.setTitle(request.title().strip());
        item.setItemType(request.itemType() == null ? SavedItemType.NOTE : request.itemType());
        if (request.url() != null && !request.url().isBlank()) {
            item.setUrl(UploadValidation.requireValidUrl(request.url()));
        }
        item.setContent(request.content());
        item.setTags(Csv.join(request.tags()));
        savedItemRepository.save(item);
        return toSavedItemResponse(item);
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeDtos.SavedItemResponse> items(String userId, SavedItemType type, String query,
                                                      int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100));
        Page<SavedItem> result;
        if (query != null && !query.isBlank()) {
            result = savedItemRepository.search(userId, query, pageable);
        } else if (type != null) {
            result = savedItemRepository.findByUserIdAndItemTypeAndDeletedAtIsNullOrderByCreatedAtDesc(userId, type, pageable);
        } else {
            result = savedItemRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);
        }
        return result.map(this::toSavedItemResponse);
    }

    @Transactional
    public KnowledgeDtos.SavedItemResponse updateItem(String userId, String itemId, KnowledgeDtos.SavedItemRequest request) {
        SavedItem item = savedItemRepository.findByIdAndUserIdAndDeletedAtIsNull(itemId, userId)
                .orElseThrow(() -> AppException.notFound("Saved item not found"));
        item.setTitle(request.title().strip());
        if (request.url() != null && !request.url().isBlank()) {
            item.setUrl(UploadValidation.requireValidUrl(request.url()));
        }
        item.setContent(request.content());
        item.setTags(Csv.join(request.tags()));
        if (request.itemType() != null) {
            item.setItemType(request.itemType());
        }
        savedItemRepository.save(item);
        return toSavedItemResponse(item);
    }

    @Transactional
    public void deleteItem(String userId, String itemId) {
        SavedItem item = savedItemRepository.findByIdAndUserIdAndDeletedAtIsNull(itemId, userId)
                .orElseThrow(() -> AppException.notFound("Saved item not found"));
        item.setDeletedAt(java.time.Instant.now());
        savedItemRepository.save(item);
    }

    // ---------------------------------------------------------------- helpers

    @Transactional(readOnly = true)
    public KnowledgeDocument requireOwned(String userId, String documentId) {
        return documentRepository.findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> AppException.notFound("Document not found"));
    }

    private Path resolveStoragePath(String userId, String documentId, String filename) {
        String safeName = filename.replaceAll("[^A-Za-z0-9._\\-]", "_");
        return storageProperties.knowledgePath().resolve(userId).resolve(documentId + "_" + safeName);
    }

    private KnowledgeDtos.DocumentResponse toResponse(KnowledgeDocument document) {
        return new KnowledgeDtos.DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getFilename(),
                document.getContentType(),
                document.getExtension(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getFailureReason(),
                document.getChunkCount(),
                document.getWordCount(),
                document.getCreatedAt());
    }

    private KnowledgeDtos.SavedItemResponse toSavedItemResponse(SavedItem item) {
        return new KnowledgeDtos.SavedItemResponse(item.getId(), item.getTitle(), item.getUrl(), item.getContent(),
                Csv.splitToList(item.getTags()), item.getItemType().name(), item.getCreatedAt());
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}