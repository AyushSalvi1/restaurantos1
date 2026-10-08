-- ============================================================================
-- LIFEOS :: V1 :: Core identity, authorization, preferences and platform tables
-- Written in the portable SQL subset understood by both MySQL 8 and H2
-- (MODE=MySQL) so a single source of truth drives every environment.
-- ============================================================================

CREATE TABLE roles (
    id           VARCHAR(36)  NOT NULL,
    code         VARCHAR(32)  NOT NULL,
    name         VARCHAR(64)  NOT NULL,
    description  VARCHAR(255),
    created_at   DATETIME(3)  NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    id           VARCHAR(36)  NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    description  VARCHAR(255),
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    id            VARCHAR(36) NOT NULL,
    role_id       VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (id),
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE TABLE users (
    id                   VARCHAR(36)  NOT NULL,
    email                VARCHAR(190) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    role_code            VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status               VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    avatar_url           VARCHAR(500),
    timezone             VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    locale               VARCHAR(16)  NOT NULL DEFAULT 'en',
    occupation           VARCHAR(120),
    employment_type      VARCHAR(32),
    email_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    onboarding_completed BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        DATETIME(3),
    created_at           DATETIME(3)  NOT NULL,
    updated_at           DATETIME(3)  NOT NULL,
    deleted_at           DATETIME(3),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role ON users (role_code);

CREATE TABLE user_preferences (
    id                             VARCHAR(36) NOT NULL,
    user_id                        VARCHAR(36) NOT NULL,
    working_hours_start            TIME,
    working_hours_end              TIME,
    day_start                      TIME,
    day_end                        TIME,
    preferred_productivity_style   VARCHAR(32),
    preferred_focus_minutes        INT          NOT NULL DEFAULT 25,
    break_minutes                  INT          NOT NULL DEFAULT 5,
    weekly_productivity_hours      DECIMAL(5,2) NOT NULL DEFAULT 20.00,
    areas_of_interest              TEXT,
    current_skills                 TEXT,
    primary_goal_areas             TEXT,
    financial_goals                TEXT,
    learning_goals                 TEXT,
    life_balance_weights           TEXT,
    created_at                     DATETIME(3) NOT NULL,
    updated_at                     DATETIME(3) NOT NULL,
    CONSTRAINT pk_user_preferences PRIMARY KEY (id),
    CONSTRAINT uk_user_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE notification_preferences (
    id                    VARCHAR(36) NOT NULL,
    user_id               VARCHAR(36) NOT NULL,
    in_app_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    task_enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    goal_enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    habit_enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    calendar_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    finance_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    learning_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_insight_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    reminder_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    quiet_hours_start     TIME,
    quiet_hours_end       TIME,
    created_at            DATETIME(3) NOT NULL,
    updated_at            DATETIME(3) NOT NULL,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id             VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    token_hash     VARCHAR(128) NOT NULL,
    issued_at      DATETIME(3)  NOT NULL,
    expires_at     DATETIME(3)  NOT NULL,
    revoked        BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_reason VARCHAR(64),
    user_agent     VARCHAR(300),
    ip_address     VARCHAR(64),
    created_at     DATETIME(3)  NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, revoked);

CREATE TABLE email_verification_tokens (
    id          VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  DATETIME(3)  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)  NOT NULL,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
    id          VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  DATETIME(3)  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)  NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE system_settings (
    id            VARCHAR(36)  NOT NULL,
    setting_key   VARCHAR(120) NOT NULL,
    setting_value TEXT,
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    description   VARCHAR(400),
    updated_by    VARCHAR(36),
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    CONSTRAINT pk_system_settings PRIMARY KEY (id),
    CONSTRAINT uk_system_settings_key UNIQUE (setting_key)
);

CREATE TABLE audit_logs (
    id            VARCHAR(36)  NOT NULL,
    actor_user_id VARCHAR(36),
    action        VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(48),
    entity_id     VARCHAR(36),
    details       TEXT,
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(300),
    created_at    DATETIME(3)  NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id, created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

CREATE TABLE error_logs (
    id              VARCHAR(36)   NOT NULL,
    severity        VARCHAR(16)   NOT NULL,
    message         VARCHAR(1000),
    path            VARCHAR(300),
    method          VARCHAR(10),
    user_id         VARCHAR(36),
    exception_class VARCHAR(255),
    stack_trace     TEXT,
    created_at      DATETIME(3)   NOT NULL,
    CONSTRAINT pk_error_logs PRIMARY KEY (id)
);
CREATE INDEX idx_error_logs_created ON error_logs (created_at);
CREATE INDEX idx_error_logs_user ON error_logs (user_id);