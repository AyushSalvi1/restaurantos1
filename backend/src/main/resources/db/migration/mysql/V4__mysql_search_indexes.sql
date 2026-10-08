-- ============================================================================
-- LIFEOS :: MySQL-only optimisation migration.
-- Applied exclusively on the MySQL profile; H2 skips this location.
-- Adds full-text search for the global knowledge/journal search endpoint.
-- ============================================================================

ALTER TABLE journal_entries
    ADD FULLTEXT INDEX ft_journal_title_body (title, content);

ALTER TABLE saved_items
    ADD FULLTEXT INDEX ft_saved_items_title_content (title, content);

ALTER TABLE knowledge_documents
    ADD FULLTEXT INDEX ft_knowledge_documents_title (title);

ALTER TABLE users
    ADD FULLTEXT INDEX ft_users_name (full_name);