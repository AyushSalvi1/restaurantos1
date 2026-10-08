package com.lifeos.rag;

import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiMessage;
import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import com.lifeos.ai.Prompts;
import com.lifeos.dto.KnowledgeDtos;
import com.lifeos.entity.KnowledgeChunk;
import com.lifeos.entity.KnowledgeDocument;
import com.lifeos.repository.KnowledgeChunkRepository;
import com.lifeos.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Retrieval-augmented generation over the user's own uploaded documents.
 *
 * <p>The grounding contract is strict: when retrieval finds nothing above the relevance floor the
 * answer is an explicit "not in your knowledge base" response. That is what stops the assistant
 * from inventing content the user never saved.</p>
 */
@Service
public class RagService {

    private static final double MIN_RELEVANCE = 0.05;

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final AiProviderRegistry providerRegistry;

    public RagService(KnowledgeDocumentRepository documentRepository,
                      KnowledgeChunkRepository chunkRepository,
                      EmbeddingService embeddingService,
                      VectorStore vectorStore,
                      AiProviderRegistry providerRegistry) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.providerRegistry = providerRegistry;
    }

    /** Ensures the per-user vector index is populated before the first query. */
    @Transactional(readOnly = true)
    public void ensureIndexed(String userId) {
        List<KnowledgeDocument> ready = documentRepository.findByUserIdAndDeletedAtIsNullAndStatus(
                userId, com.lifeos.entity.enums.KnowledgeStatus.READY);
        for (KnowledgeDocument document : ready) {
            List<KnowledgeChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());
            vectorStore.replaceDocument(userId, document.getId(), chunks);
        }
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDtos.SearchHit> search(String userId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        ensureIndexed(userId);
        float[] embedding = embeddingService.embed(List.of(query)).get(0);
        List<VectorStore.ScoredChunk> scored = vectorStore.search(userId, embedding, topK);

        Map<String, KnowledgeDocument> documents = documentRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, doc -> doc, (a, b) -> a));

        List<KnowledgeDtos.SearchHit> hits = new ArrayList<>();
        for (VectorStore.ScoredChunk match : scored) {
            if (match.score() < MIN_RELEVANCE) {
                continue;
            }
            KnowledgeChunk chunk = match.chunk();
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            hits.add(new KnowledgeDtos.SearchHit(
                    chunk.getId(),
                    document.getId(),
                    document.getTitle(),
                    document.getFilename(),
                    chunk.getChunkIndex(),
                    snippet(chunk.getContent()),
                    Math.round(match.score() * 1000) / 1000.0));
        }
        hits.sort(Comparator.comparingDouble(KnowledgeDtos.SearchHit::score).reversed());
        return hits;
    }

    /**
     * Answers a question grounded in retrieved excerpts. Returns an explicit non-grounded answer
     * when nothing relevant was found rather than falling back to general model knowledge.
     */
    public GroundedAnswer answer(String userId, String question, int topK) {
        List<KnowledgeDtos.SearchHit> hits = search(userId, question, topK);
        if (hits.isEmpty()) {
            return new GroundedAnswer(
                    "I could not find anything about that in your knowledge base. Upload a document that covers it, "
                            + "or rephrase the question.",
                    List.of(), false, "none", "none", List.of());
        }

        StringBuilder prompt = new StringBuilder("EXCERPTS FROM YOUR KNOWLEDGE BASE\n");
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeDtos.SearchHit hit = hits.get(i);
            prompt.append('[').append(i + 1).append("] ")
                    .append(hit.documentTitle()).append(" - ").append(hit.filename())
                    .append(" (chunk ").append(hit.chunkIndex()).append(", relevance ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", hit.score())).append(")\n")
                    .append(hit.snippet()).append("\n\n");
            sources.add(hit.documentTitle());
        }
        prompt.append("\nQUESTION: ").append(question);

        AIProvider provider = providerRegistry.active();
        AiResponse response = provider.complete(AiRequest.of(List.of(
                AiMessage.system(Prompts.RAG_SYSTEM),
                AiMessage.user(prompt.toString()))));
        return new GroundedAnswer(response.text(), hits, true, response.provider(), response.model(), sources);
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String normalised = content.replaceAll("\\s+", " ").strip();
        return normalised.length() <= 320 ? normalised : normalised.substring(0, 320) + "...";
    }

    public record GroundedAnswer(
            String text,
            List<KnowledgeDtos.SearchHit> citations,
            boolean grounded,
            String provider,
            String model,
            List<String> sourceTitles
    ) {
    }
}