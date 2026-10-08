-- ============================================================================
-- LIFEOS :: V3 :: Life domains: learning, finance, journal, knowledge base,
-- AI conversations and the notification engine.
-- ============================================================================

CREATE TABLE skills (
    id          VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    category    VARCHAR(48),
    proficiency INT         NOT NULL DEFAULT 1,
    created_at  DATETIME(3) NOT NULL,
    updated_at  DATETIME(3) NOT NULL,
    CONSTRAINT pk_skills PRIMARY KEY (id),
    CONSTRAINT fk_skills_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_skills_user ON skills (user_id);

CREATE TABLE learning_goals (
    id           VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    category     VARCHAR(48),
    target_date  DATETIME(3),
    progress     INT          NOT NULL DEFAULT 0,
    status       VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    hours_spent  DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    skill_id     VARCHAR(36),
    source       VARCHAR(16)  NOT NULL DEFAULT 'USER',
    ai_confirmed BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    deleted_at   DATETIME(3),
    CONSTRAINT pk_learning_goals PRIMARY KEY (id),
    CONSTRAINT fk_learning_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_goals_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE SET NULL
);
CREATE INDEX idx_learning_goals_user ON learning_goals (user_id, deleted_at, status);

CREATE TABLE learning_topics (
    id                 VARCHAR(36)  NOT NULL,
    learning_goal_id   VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    position           INT          NOT NULL DEFAULT 0,
    completed          BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at       DATETIME(3),
    estimated_minutes  INT          NOT NULL DEFAULT 60,
    created_at         DATETIME(3)  NOT NULL,
    updated_at         DATETIME(3)  NOT NULL,
    CONSTRAINT pk_learning_topics PRIMARY KEY (id),
    CONSTRAINT fk_learning_topics_goal FOREIGN KEY (learning_goal_id) REFERENCES learning_goals (id) ON DELETE CASCADE
);
CREATE INDEX idx_learning_topics_goal ON learning_topics (learning_goal_id, position);

CREATE TABLE learning_sessions (
    id              VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    learning_goal_id VARCHAR(36),
    topic_id        VARCHAR(36),
    started_at      DATETIME(3)  NOT NULL,
    ended_at        DATETIME(3),
    minutes         INT          NOT NULL DEFAULT 0,
    notes           TEXT,
    quiz_score      DECIMAL(5,2),
    created_at      DATETIME(3)  NOT NULL,
    CONSTRAINT pk_learning_sessions PRIMARY KEY (id),
    CONSTRAINT fk_learning_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_sessions_goal FOREIGN KEY (learning_goal_id) REFERENCES learning_goals (id) ON DELETE SET NULL,
    CONSTRAINT fk_learning_sessions_topic FOREIGN KEY (topic_id) REFERENCES learning_topics (id) ON DELETE SET NULL
);
CREATE INDEX idx_learning_sessions_user ON learning_sessions (user_id, started_at);

CREATE TABLE learning_resources (
    id               VARCHAR(36)  NOT NULL,
    user_id          VARCHAR(36)  NOT NULL,
    learning_goal_id VARCHAR(36),
    title            VARCHAR(200) NOT NULL,
    url              VARCHAR(500),
    resource_type    VARCHAR(32)  NOT NULL DEFAULT 'ARTICLE',
    completed        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(3)  NOT NULL,
    updated_at       DATETIME(3)  NOT NULL,
    CONSTRAINT pk_learning_resources PRIMARY KEY (id),
    CONSTRAINT fk_learning_resources_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_resources_goal FOREIGN KEY (learning_goal_id) REFERENCES learning_goals (id) ON DELETE SET NULL
);

CREATE TABLE finance_transactions (
    id               VARCHAR(36)   NOT NULL,
    user_id          VARCHAR(36)   NOT NULL,
    transaction_type VARCHAR(16)   NOT NULL,
    amount           DECIMAL(15,2) NOT NULL,
    currency         VARCHAR(8)    NOT NULL DEFAULT 'USD',
    category         VARCHAR(48)   NOT NULL,
    description      VARCHAR(500),
    occurred_on      DATE          NOT NULL,
    is_recurring     BOOLEAN       NOT NULL DEFAULT FALSE,
    recurrence_rule  VARCHAR(120),
    account          VARCHAR(80),
    created_at       DATETIME(3)   NOT NULL,
    updated_at       DATETIME(3)   NOT NULL,
    deleted_at       DATETIME(3),
    CONSTRAINT pk_finance_transactions PRIMARY KEY (id),
    CONSTRAINT fk_finance_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_finance_tx_user_date ON finance_transactions (user_id, occurred_on, deleted_at);
CREATE INDEX idx_finance_tx_user_cat ON finance_transactions (user_id, category);

CREATE TABLE budgets (
    id         VARCHAR(36)   NOT NULL,
    user_id    VARCHAR(36)   NOT NULL,
    category   VARCHAR(48),
    period     VARCHAR(16)   NOT NULL DEFAULT 'MONTHLY',
    amount     DECIMAL(15,2) NOT NULL,
    start_date DATE          NOT NULL,
    end_date   DATE          NOT NULL,
    created_at DATETIME(3)   NOT NULL,
    updated_at DATETIME(3)   NOT NULL,
    CONSTRAINT pk_budgets PRIMARY KEY (id),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_budgets_user ON budgets (user_id, start_date, end_date);

CREATE TABLE savings_goals (
    id            VARCHAR(36)   NOT NULL,
    user_id       VARCHAR(36)   NOT NULL,
    name          VARCHAR(160)  NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    saved_amount  DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    target_date   DATE,
    notes         TEXT,
    created_at    DATETIME(3)   NOT NULL,
    updated_at    DATETIME(3)   NOT NULL,
    CONSTRAINT pk_savings_goals PRIMARY KEY (id),
    CONSTRAINT fk_savings_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE journal_entries (
    id           VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    title        VARCHAR(200),
    content      TEXT         NOT NULL,
    entry_date   DATE         NOT NULL,
    mood         VARCHAR(24),
    mood_score   INT,
    tags         TEXT,
    ai_analyzed  BOOLEAN      NOT NULL DEFAULT FALSE,
    ai_summary   TEXT,
    word_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    deleted_at   DATETIME(3),
    CONSTRAINT pk_journal_entries PRIMARY KEY (id),
    CONSTRAINT fk_journal_entries_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_journal_user_date ON journal_entries (user_id, entry_date, deleted_at);

CREATE TABLE knowledge_documents (
    id             VARCHAR(36)   NOT NULL,
    user_id        VARCHAR(36)   NOT NULL,
    title          VARCHAR(255)  NOT NULL,
    filename       VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(120),
    extension      VARCHAR(16)   NOT NULL,
    size_bytes     BIGINT        NOT NULL DEFAULT 0,
    storage_path   VARCHAR(500),
    status         VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500),
    chunk_count    INT           NOT NULL DEFAULT 0,
    word_count     INT           NOT NULL DEFAULT 0,
    created_at     DATETIME(3)   NOT NULL,
    updated_at     DATETIME(3)   NOT NULL,
    deleted_at     DATETIME(3),
    CONSTRAINT pk_knowledge_documents PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_documents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_knowledge_docs_user ON knowledge_documents (user_id, deleted_at, status);

CREATE TABLE knowledge_chunks (
    id               VARCHAR(36) NOT NULL,
    document_id      VARCHAR(36) NOT NULL,
    user_id          VARCHAR(36) NOT NULL,
    chunk_index      INT        NOT NULL,
    content          TEXT       NOT NULL,
    token_estimate   INT        NOT NULL DEFAULT 0,
    embedding        TEXT,
    embedding_model  VARCHAR(64),
    created_at       DATETIME(3) NOT NULL,
    CONSTRAINT pk_knowledge_chunks PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_chunks_doc FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    CONSTRAINT uk_knowledge_chunk UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_knowledge_chunks_user ON knowledge_chunks (user_id);

CREATE TABLE saved_items (
    id         VARCHAR(36)  NOT NULL,
    user_id    VARCHAR(36)  NOT NULL,
    item_type  VARCHAR(24)  NOT NULL DEFAULT 'NOTE',
    title      VARCHAR(255) NOT NULL,
    url        VARCHAR(700),
    content    TEXT,
    tags       TEXT,
    created_at DATETIME(3)  NOT NULL,
    updated_at DATETIME(3)  NOT NULL,
    deleted_at DATETIME(3),
    CONSTRAINT pk_saved_items PRIMARY KEY (id),
    CONSTRAINT fk_saved_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_saved_items_user ON saved_items (user_id, item_type, deleted_at);

CREATE TABLE ai_conversations (
    id              VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message_count   INT          NOT NULL DEFAULT 0,
    last_message_at DATETIME(3),
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    deleted_at      DATETIME(3),
    CONSTRAINT pk_ai_conversations PRIMARY KEY (id),
    CONSTRAINT fk_ai_conversations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_conversations_user ON ai_conversations (user_id, deleted_at, updated_at);

CREATE TABLE ai_messages (
    id                VARCHAR(36)  NOT NULL,
    conversation_id   VARCHAR(36)  NOT NULL,
    user_id           VARCHAR(36)  NOT NULL,
    role              VARCHAR(16)  NOT NULL,
    content           TEXT         NOT NULL,
    provider          VARCHAR(32),
    model             VARCHAR(80),
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    citations         TEXT,
    created_at        DATETIME(3)  NOT NULL,
    CONSTRAINT pk_ai_messages PRIMARY KEY (id),
    CONSTRAINT fk_ai_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_messages_conversation ON ai_messages (conversation_id, created_at);

CREATE TABLE notifications (
    id             VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    category       VARCHAR(24)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    body           VARCHAR(700),
    link           VARCHAR(300),
    priority       VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    read_at        DATETIME(3),
    dedupe_key     VARCHAR(190),
    scheduled_for  DATETIME(3),
    created_at     DATETIME(3)  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_user ON notifications (user_id, read_at, created_at);
CREATE UNIQUE INDEX uk_notifications_dedupe ON notifications (user_id, dedupe_key);