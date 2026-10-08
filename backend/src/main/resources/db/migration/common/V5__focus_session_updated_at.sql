-- focus_sessions is mapped by an entity that carries the shared audit superclass, so it needs the same
-- updated_at column as every other audited table. Without it Hibernate selects a column that does not
-- exist and every read of a focus session fails at runtime.
ALTER TABLE focus_sessions ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);