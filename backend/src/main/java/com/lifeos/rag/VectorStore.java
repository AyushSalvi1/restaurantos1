package com.lifeos.rag;

import com.lifeos.entity.KnowledgeChunk;

import java.util.List;

/**
 * Storage abstraction for chunk embeddings.
 *
 * <p>MySQL has no native vector type, so the default implementation keeps a per-user in-memory
 * index rebuilt from the persisted {@code knowledge_chunks} rows. That keeps retrieval fast for
 * personal knowledge bases while leaving a Qdrant or pgvector implementation a drop-in for larger
 * corpora.</p>
 */
public interface VectorStore {

    void replaceDocument(String userId, String documentId, List<KnowledgeChunk> chunks);

    void removeDocument(String userId, String documentId);

    void removeAll(String userId);

    /** Highest-scoring chunks for the query embedding, already filtered to the owning user. */
    List<ScoredChunk> search(String userId, float[] queryEmbedding, int topK);

    record ScoredChunk(KnowledgeChunk chunk, double score) {
    }
}