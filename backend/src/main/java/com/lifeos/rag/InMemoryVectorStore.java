package com.lifeos.rag;

import com.lifeos.ai.provider.LocalEmbedder;
import com.lifeos.entity.KnowledgeChunk;
import com.lifeos.repository.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory cosine-similarity index, scoped per user and rebuilt from the database on startup.
 *
 * <p>Vectors stay owned by MySQL, so the cache is purely derived state: deleting it costs nothing
 * but a re-read, which keeps the store stateless with respect to durability.</p>
 */
@Component
public class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final LocalEmbedder localEmbedder;
    private final Map<String, List<IndexedChunk>> index = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryVectorStore(KnowledgeChunkRepository chunkRepository,
                               EmbeddingService embeddingService,
                               LocalEmbedder localEmbedder) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.localEmbedder = localEmbedder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long chunks = chunkRepository.count();
        if (chunks > 0) {
            log.info("Knowledge vector index ready with {} persisted chunks", chunks);
        }
    }

    @Override
    public void replaceDocument(String userId, String documentId, List<KnowledgeChunk> chunks) {
        lock.writeLock().lock();
        try {
            List<IndexedChunk> existing = index.computeIfAbsent(userId, key -> new ArrayList<>());
            existing.removeIf(entry -> entry.chunk.getDocumentId().equals(documentId));
            for (KnowledgeChunk chunk : chunks) {
                float[] vector = embeddingService.deserialise(chunk.getEmbedding());
                if (vector.length > 0) {
                    existing.add(new IndexedChunk(chunk, vector));
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeDocument(String userId, String documentId) {
        lock.writeLock().lock();
        try {
            List<IndexedChunk> existing = index.get(userId);
            if (existing != null) {
                existing.removeIf(entry -> entry.chunk.getDocumentId().equals(documentId));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll(String userId) {
        lock.writeLock().lock();
        try {
            index.remove(userId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ScoredChunk> search(String userId, float[] queryEmbedding, int topK) {
        if (queryEmbedding.length == 0) {
            return List.of();
        }
        List<IndexedChunk> candidates;
        lock.readLock().lock();
        try {
            candidates = index.get(userId);
        } finally {
            lock.readLock().unlock();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> new ScoredChunk(candidate.chunk,
                        localEmbedder.cosine(queryEmbedding, candidate.vector)))
                .filter(scored -> scored.score() > 0.02)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    private record IndexedChunk(KnowledgeChunk chunk, float[] vector) {
    }
}